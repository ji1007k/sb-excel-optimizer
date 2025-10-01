package com.performance.excel.strategy.impl;

import com.performance.excel.dto.DownloadProgress;
import com.performance.excel.dto.DownloadRequest;
import com.performance.excel.entity.TestData;
import com.performance.excel.strategy.ExcelContext;
import com.performance.excel.strategy.ExcelDownloadStrategy;
import com.performance.excel.util.TestDataExcelBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.springframework.stereotype.Component;

import java.io.FileOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * FastExcel 라이브러리 전략 (비동기)
 * 
 * 특징:
 * - FastExcel 사용 (POI 의존성 없음)
 * - XLSX 파일만 지원
 * - 메모리 효율적인 스트리밍 방식
 * - ID 커서 기반 페이징
 * - Service 레이어에서 BlockingQueue로 비동기 처리
 * 
 * 성능:
 * - 메모리: ~22MB
 * - 처리량: 28.5개/분
 * - 응답시간: ~4ms (즉시)
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FastexcelStrategy implements ExcelDownloadStrategy {

    private final TestDataExcelBuilder excelBuilder;
    private static final int CHUNK_SIZE = 1000;

    @Override
    public void process(DownloadRequest request, ExcelContext context) {
        log.info("FastExcel 방식 처리 시작: {}", request.getRequestId());

        long totalCount = context.getTestDataRepository().getTotalCount();
        String filePath = excelBuilder.getDownloadPath(context.getDownloadDirectory(), request.getFileName());

        log.info("파일 저장 예정 경로: {}", filePath);

        try (FileOutputStream fos = new FileOutputStream(filePath);
            Workbook wb = new Workbook(fos, "ExcelOptimizer", "1.0")) {

            Worksheet worksheet = wb.newWorksheet("Test Data");

            // 시트 스타일 설정 (헤더)
            String[] headers = {"ID", "이름", "설명", "값", "카테고리", "생성일시"};
            for (int i = 0; i < headers.length; i++) {
                worksheet.value(0, i, headers[i]);
                worksheet.style(0, i)
                        .bold()
                        .horizontalAlignment("center")
                        .fillColor("d3d3d3")
                        .set();
            }

            // ID 커서 기반 쿼리
            String cursorSql = """
                SELECT id, name, description, value, category, created_at
                FROM test_data
                WHERE id > ? 
                ORDER BY id
                LIMIT ?
            """;

            int currentRow = 1; // 헤더 다음부터
            long processedCount = 0L;
            Long lastId = 0L;
            
            while (true) {
                List<TestData> excelData = context.getJdbcTemplate().query(cursorSql,
                    (resultSet, rowNum) -> TestData.builder()
                            .id(resultSet.getLong("id"))
                            .name(resultSet.getString("name"))
                            .description(resultSet.getString("description"))
                            .value(resultSet.getBigDecimal("value"))
                            .category(resultSet.getString("category"))
                            .createdAt(resultSet.getTimestamp("created_at").toLocalDateTime())
                            .build(),
                    lastId, CHUNK_SIZE);

                if (excelData.isEmpty()) break;

                lastId = excelData.get(excelData.size() - 1).getId();

                for (TestData rowData : excelData) {
                    worksheet.value(currentRow, 0, rowData.getId());
                    worksheet.value(currentRow, 1, rowData.getName());
                    worksheet.value(currentRow, 2, rowData.getDescription());
                    worksheet.value(currentRow, 3, rowData.getValue());
                    worksheet.value(currentRow, 4, rowData.getCategory());
                    worksheet.value(currentRow, 5, rowData.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

                    processedCount++;
                    currentRow++;

                    worksheet.flush();  // 행 단위로 즉시 디스크 쓰기

                    // 진행률 업데이트 빈도 조절 (5000건마다)
                    if (processedCount % 5000 == 0) {
                        DownloadProgress progress = DownloadProgress.processing(
                                request.getRequestId(), totalCount, processedCount);
                        try {
                            context.getProgressWebSocketHandler().sendProgress(request.getUserId(), progress);
                        } catch (Exception e) {
                            log.warn("진행률 전송 실패: {}", e.getMessage());
                        }
                    }
                }
                
                log.debug("FastExcel 청크 처리 완료: ID {}-{} (총 {}건)", lastId - CHUNK_SIZE, lastId, processedCount);
            }

            wb.finish();    // 필수 호출

            // 완료 알림
            String downloadUrl = "/api/download/file/" + request.getFileName();
            DownloadProgress completedProgress = DownloadProgress.completed(request.getRequestId(), downloadUrl);
            try {
                context.getProgressWebSocketHandler().sendProgress(request.getUserId(), completedProgress);
            } catch (Exception e) {
                log.warn("완료 알림 전송 실패: {}", e.getMessage());
            }

            log.info("FastExcel 파일 생성 완료: {} ({}건)", filePath, processedCount);

        } catch (Exception e) {
            log.error("FastExcel 다운로드 실패: {}", request.getRequestId(), e);
            throw new RuntimeException("FastExcel 다운로드 실패: " + e.getMessage(), e);
        }
    }

    @Override
    public DownloadRequest.DownloadType getSupportedType() {
        return DownloadRequest.DownloadType.FASTEXCEL;
    }
}
