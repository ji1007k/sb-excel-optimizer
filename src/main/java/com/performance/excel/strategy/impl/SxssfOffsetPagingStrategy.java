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
 * SXSSF OFFSET(ROWNUM) 페이징 전략 (동기)
 * 
 * 특징:
 * - SXSSFWorkbook 사용 (메모리 10행만 유지)
 * - ROWNUM(OFFSET) 기반 페이징
 * - 안정적이지만 쿼리 오버헤드 있음
 * 
 * 성능:
 * - 메모리: ~50MB
 * - 처리량: 8.5개/분
 * - 응답시간: ~8초
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SxssfOffsetPagingStrategy implements ExcelDownloadStrategy {
    
    private final TestDataExcelBuilder excelBuilder;
    private static final int BATCH_SIZE = 1000;
    
    @Override
    public void process(DownloadRequest request, ExcelContext context) {
        log.info("SXSSF OFFSET 페이징 방식 처리 시작: {}", request.getRequestId());
        
        long totalCount = context.getTestDataRepository().getTotalCount();
        String filePath = excelBuilder.getDownloadPath(context.getDownloadDirectory(), request.getFileName());
        
        try (SXSSFWorkbook workbook = excelBuilder.createSXSSFWorkbook(10)) {
            // 시트 설정 (컬럼 너비 + 헤더)
            Sheet sheet = excelBuilder.setupSheet(workbook, "Test Data");
            CellStyle dataStyle = excelBuilder.createDataStyle(workbook);
            
            int currentRow = 1; // 헤더 다음부터
            long processedCount = 0;
            
            // ROWNUM 기반 페이징 처리
            for (long startRow = 1; startRow <= totalCount; startRow += BATCH_SIZE) {
                long endRow = Math.min(startRow + BATCH_SIZE - 1, totalCount);
                
                // ROWNUM(OFFSET) 쿼리로 데이터 조회
                String rownumSql = """
                    SELECT * FROM (
                        SELECT ROW_NUMBER() OVER (ORDER BY id) as rnum, 
                               id, name, description, value, category, created_at 
                        FROM test_data
                    ) 
                    WHERE rnum BETWEEN ? AND ?
                    """;
                
                List<Object[]> pageData = context.getJdbcTemplate().query(rownumSql, 
                    new Object[]{startRow, endRow}, 
                    (rs, rowNum) -> new Object[]{
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getBigDecimal("value"),
                        rs.getString("category"),
                        rs.getTimestamp("created_at")
                    });
                
                // 페이지 데이터를 즉시 엑셀에 쓰기
                for (Object[] rowData : pageData) {
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
                
                // 페이지 처리 완료 로그
                log.debug("ROWNUM 페이지 처리 완료: {}-{} (총 {}건)", startRow, endRow, processedCount);
                
                // 시뮬레이션을 위한 지연
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("다운로드 중단됨", e);
                }
            }
            
            // 파일 저장
            excelBuilder.saveWorkbook(workbook, filePath);
            
            // 완료 알림
            String downloadUrl = "/api/download/file/" + request.getFileName();
            DownloadProgress completedProgress = DownloadProgress.completed(request.getRequestId(), downloadUrl);
            try {
                context.getProgressWebSocketHandler().sendProgress(request.getUserId(), completedProgress);
            } catch (Exception e) {
                log.warn("완료 알림 전송 실패: {}", e.getMessage());
            }
            
            log.info("SXSSF OFFSET 페이징 파일 생성 완료: {} ({}건)", request.getFileName(), processedCount);
            
        } catch (Exception e) {
            log.error("SXSSF OFFSET 페이징 파일 생성 실패: {}", request.getRequestId(), e);
            throw new RuntimeException("SXSSF OFFSET 페이징 파일 생성 실패: " + e.getMessage(), e);
        }
    }
    
    @Override
    public DownloadRequest.DownloadType getSupportedType() {
        return DownloadRequest.DownloadType.SXSSF_OFFSET_PAGING;
    }
}
