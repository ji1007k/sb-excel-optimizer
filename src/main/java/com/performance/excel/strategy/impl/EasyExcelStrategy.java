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

// 엔티티 사용 -> 타입 안정성 향상
@Slf4j
@Component
@RequiredArgsConstructor
public class EasyExcelStrategy implements ExcelDownloadStrategy {

    private final TestDataExcelBuilder excelBuilder;

    // DB round-trip 최소화 지점 탐색 필요
    private static final int CHUNK_SIZE = 1000;
    // 진행률 업데이트 주기
    private static final int PROGRESS_UPDATE_INTERVAL = 50000;
    
    @Override
    public void process(DownloadRequest request, ExcelContext context) {
//        throw new UnsupportedOperationException("EasyExcel processing not implemented yet");
        log.info("Processing with EASY_EXCEL method: {}", request.getRequestId());

        long totalCount = context.getTestDataRepository().getTotalCount();
        String filePath = excelBuilder.getDownloadPath(context.getDownloadDirectory(), request.getFileName());

        // SXSSFWorkbook(10) → EasyExcel.write()
        // 엔티티에서 어노테이션 정보 토대로 헤더명, 컬럼너비 등 자동 세팅
        try (ExcelWriter excelWriter = EasyExcel.write(filePath, TestData.class).build()) {
            // 복잡한 시트 설정 → 간단한 writerSheet()
            WriteSheet writeSheet = EasyExcel.writerSheet("Test Data").build(); // 엑셀 시트(탭) 생성

            long processedCount = 0;
            Long lastId = 0L;
            while (true) {
                String chunkSql = """
                            SELECT id, name, description, value, category, created_at 
                            FROM test_data
                            WHERE id > ? 
                            ORDER BY id 
                            LIMIT ?
                        """;

                // ✅ 개선된 방식: 1단계 변환 (효율적)
                List<TestData> excelDatas = context.getJdbcTemplate().query(chunkSql,
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

                // 바로 사용
                excelWriter.write(excelDatas, writeSheet);

                // 진행률 + 메모리 정리
                processedCount += excelDatas.size();
                lastId = excelDatas.get(excelDatas.size() - 1).getId(); // 마지막 ID 저장

                // 진행률 업데이트 빈도 조절 (5000건마다 - WebSocket 부하 줄이기)
                if (processedCount % PROGRESS_UPDATE_INTERVAL == 0) {
                    DownloadProgress progress = DownloadProgress.processing(
                            request.getRequestId(), totalCount, processedCount);
                    try {
                        context.getProgressWebSocketHandler().sendProgress(request.getUserId(), progress);
                    } catch (Exception e) {
                        log.warn("Failed to send progress update: {}", e.getMessage());
                    }
                }

                log.debug("Processed chunk: {}-{} ({} total rows)", lastId - CHUNK_SIZE, lastId, processedCount);
            }

            log.info("EasyExcel file created: {} ({} rows)", filePath, processedCount);
        }
    }
    
    @Override
    public DownloadRequest.DownloadType getSupportedType() {
        return DownloadRequest.DownloadType.EASY_EXCEL;
    }
}
