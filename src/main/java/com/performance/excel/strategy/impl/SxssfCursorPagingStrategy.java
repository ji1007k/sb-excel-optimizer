package com.performance.excel.strategy.impl;

import com.performance.excel.dto.DownloadProgress;
import com.performance.excel.dto.DownloadRequest;
import com.performance.excel.strategy.ExcelContext;
import com.performance.excel.strategy.ExcelDownloadStrategy;
import com.performance.excel.util.TestDataExcelBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * SXSSF CURSOR 페이징 전략 (비동기) - 권장
 * 
 * 특징:
 * - SXSSFWorkbook 사용 (메모리 10행만 유지)
 * - ID 커서 기반 페이징
 * - Service 레이어에서 BlockingQueue로 비동기 처리
 * 
 * 성능:
 * - 메모리: ~18MB
 * - 처리량: 32.3개/분
 * - 응답시간: ~2ms (즉시)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SxssfCursorPagingStrategy implements ExcelDownloadStrategy {

    private final TestDataExcelBuilder excelBuilder;
    private static final int CHUNK_SIZE = 1000;

    @Override
    public DownloadRequest.DownloadType getSupportedType() {
        return DownloadRequest.DownloadType.SXSSF_CURSOR_PAGING;
    }

    @Override
    public void process(DownloadRequest request, ExcelContext context) {
        log.info("SXSSF CURSOR 페이징 방식 처리 시작: {}", request.getRequestId());

        long totalCount = context.getTestDataRepository().getTotalCount();
        String filePath = excelBuilder.getDownloadPath(context.getDownloadDirectory(), request.getFileName());

        log.info("파일 저장 예정 경로: {}", filePath);

        try {
            // ID 기반 커서 스트리밍으로 엑셀 직접 생성
            createExcelWithCursorStreaming(request, filePath, totalCount, context);

            // 완료 알림 (안전한 WebSocket 전송)
            String downloadUrl = "/api/download/file/" + request.getFileName();
            DownloadProgress completedProgress = DownloadProgress.completed(request.getRequestId(), downloadUrl);
            try {
                context.getProgressWebSocketHandler().sendProgress(request.getUserId(), completedProgress);
            } catch (Exception e) {
                log.warn("완료 알림 전송 실패: {}", e.getMessage());
            }

            log.info("SXSSF CURSOR 페이징 파일 생성 완료: {} ({}건)", request.getFileName(), totalCount);

        } catch (Exception e) {
            log.error("SXSSF CURSOR 페이징 다운로드 실패: {}", request.getRequestId(), e);
            throw new RuntimeException("SXSSF CURSOR 페이징 다운로드 실패: " + e.getMessage(), e);
        }
    }

    /**
     * ID 커서 기반 스트리밍 처리
     * - 1000건씩만 메모리에 로드
     * - 메모리 사용량을 일정하게 유지하는 핵심 로직
     */
    private void createExcelWithCursorStreaming(DownloadRequest request, String filePath, long totalCount, ExcelContext context) throws Exception {
        // 메모리에 10개 행만 유지 (최대 메모리 절약)
        try (SXSSFWorkbook workbook = excelBuilder.createSXSSFWorkbook(10)) {
            // 시트 설정 (컬럼 너비 + 헤더)
            Sheet sheet = excelBuilder.setupSheet(workbook, "Test Data");
            CellStyle dataStyle = excelBuilder.createDataStyle(workbook);

            int currentRow = 1; // 헤더 다음부터
            long processedCount = 0;

            // 핵심: 전체 데이터를 한 번에 로드하지 않고 커서로 청크별로 처리
            Long lastId = 0L;
            while (true) {
                // ID 커서 기반 쿼리 (인덱스 활용)
                String cursorSql = "SELECT id, name, description, value, category, created_at " +
                                  "FROM test_data WHERE id > ? ORDER BY id LIMIT ?";

                // 청크별로만 메모리에 로드 (1000건씩)
                List<Object[]> chunkData = context.getJdbcTemplate().query(cursorSql,
                    new Object[]{lastId, CHUNK_SIZE},
                    (rs, rowNum) -> new Object[]{
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getBigDecimal("value"),
                        rs.getString("category"),
                        rs.getTimestamp("created_at")
                    });

                if (chunkData.isEmpty()) break;

                lastId = (Long) chunkData.get(chunkData.size() - 1)[0]; // 마지막 ID 저장 (다음 커서 위치)

                // 청크 데이터를 즉시 Excel에 쓰기
                for (Object[] rowData : chunkData) {
                    Object[] excelRowData = {
                        rowData[0], // id
                        rowData[1], // name
                        rowData[2], // description
                        rowData[3], // value
                        rowData[4], // category
                        ((java.sql.Timestamp)rowData[5]).toLocalDateTime()
                                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) // created_at
                    };

                    excelBuilder.writeDataRow(sheet, currentRow++, excelRowData, dataStyle);
                    processedCount++;

                    // 진행률 업데이트 빈도 조절 (5000건마다 - WebSocket 부하 줄이기)
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

                // 중요: 청크 처리 후 메모리에서 제거
                chunkData.clear();

                log.debug("CURSOR 청크 처리 완료: ID {}-{} (총 {}건)", lastId - CHUNK_SIZE, lastId, processedCount);
            }

            // 파일 저장
            excelBuilder.saveWorkbook(workbook, filePath);

            log.info("SXSSF CURSOR 페이징 스트리밍 파일 생성: {} ({}건)", filePath, processedCount);
        }
    }
}
