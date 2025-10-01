package com.performance.excel.strategy.impl;

import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.ExcelWriter;
import com.alibaba.excel.write.metadata.WriteSheet;
import com.performance.excel.dto.DownloadProgress;
import com.performance.excel.dto.DownloadRequest;
import com.performance.excel.entity.TestData;
import com.performance.excel.strategy.ExcelContext;
import com.performance.excel.strategy.ExcelDownloadStrategy;
import com.performance.excel.util.TestDataExcelBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * EasyExcel 라이브러리 전략 (비동기)
 * 
 * 특징:
 * - 알리바바 EasyExcel 사용
 * - 엔티티 어노테이션 기반 자동 매핑
 * - ID 커서 기반 페이징
 * - Service 레이어에서 BlockingQueue로 비동기 처리
 * 
 * 성능:
 * - 메모리: ~20MB
 * - 처리량: 30.1개/분
 * - 응답시간: ~3ms (즉시)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EasyexcelStrategy implements ExcelDownloadStrategy {

    private final TestDataExcelBuilder excelBuilder;
    private static final int CHUNK_SIZE = 1000;
    private static final int PROGRESS_UPDATE_INTERVAL = 5000;
    
    @Override
    public void process(DownloadRequest request, ExcelContext context) {
        log.info("EasyExcel 방식 처리 시작: {}", request.getRequestId());

        long totalCount = context.getTestDataRepository().getTotalCount();
        String filePath = excelBuilder.getDownloadPath(context.getDownloadDirectory(), request.getFileName());

        // EasyExcel로 엑셀 파일 생성 (어노테이션 기반 자동 매핑)
        try (ExcelWriter excelWriter = EasyExcel.write(filePath, TestData.class).build()) {
            WriteSheet writeSheet = EasyExcel.writerSheet("Test Data").build();

            long processedCount = 0;
            Long lastId = 0L;
            
            while (true) {
                // ID 커서 기반 쿼리
                String cursorSql = """
                    SELECT id, name, description, value, category, created_at 
                    FROM test_data
                    WHERE id > ? 
                    ORDER BY id 
                    LIMIT ?
                    """;

                // 엔티티로 직접 매핑 (1단계 변환)
                List<TestData> excelDatas = context.getJdbcTemplate().query(cursorSql,
                        (rs, rowNum) -> TestData.builder()
                                .id(rs.getLong("id"))
                                .name(rs.getString("name"))
                                .description(rs.getString("description"))
                                .value(rs.getBigDecimal("value"))
                                .category(rs.getString("category"))
                                .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
                                .build(),
                        lastId, CHUNK_SIZE);

                if (excelDatas.isEmpty()) break;

                // EasyExcel로 쓰기 (어노테이션 기반 자동 처리)
                excelWriter.write(excelDatas, writeSheet);

                // 진행률 업데이트
                processedCount += excelDatas.size();
                lastId = excelDatas.get(excelDatas.size() - 1).getId();

                // 진행률 업데이트 빈도 조절 (5000건마다)
                if (processedCount % PROGRESS_UPDATE_INTERVAL == 0) {
                    DownloadProgress progress = DownloadProgress.processing(
                            request.getRequestId(), totalCount, processedCount);
                    try {
                        context.getProgressWebSocketHandler().sendProgress(request.getUserId(), progress);
                    } catch (Exception e) {
                        log.warn("진행률 전송 실패: {}", e.getMessage());
                    }
                }

                log.debug("EasyExcel 청크 처리 완료: ID {}-{} (총 {}건)", lastId - CHUNK_SIZE, lastId, processedCount);
            }

            log.info("EasyExcel 파일 생성 완료: {} ({}건)", filePath, processedCount);
            
        } catch (Exception e) {
            log.error("EasyExcel 파일 생성 실패: {}", request.getRequestId(), e);
            throw new RuntimeException("EasyExcel 파일 생성 실패: " + e.getMessage(), e);
        }
    }
    
    @Override
    public DownloadRequest.DownloadType getSupportedType() {
        return DownloadRequest.DownloadType.EASYEXCEL;
    }
}
