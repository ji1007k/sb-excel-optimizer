package com.performance.excel.strategy.impl;

import com.performance.excel.dto.DownloadProgress;
import com.performance.excel.dto.DownloadRequest;
import com.performance.excel.entity.TestData;
import com.performance.excel.strategy.ExcelContext;
import com.performance.excel.strategy.ExcelDownloadStrategy;
import com.performance.excel.util.TestDataExcelBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OldWayExcelStrategy implements ExcelDownloadStrategy {
    
    private final TestDataExcelBuilder excelBuilder;
    
    @Override
    public void process(DownloadRequest request, ExcelContext context) {
        log.warn("당시 문제 방식 처리: {}", request.getRequestId());
        
        // 전체 데이터 한번에 조회 (문제!)
        List<TestData> allData = context.getTestDataRepository().findAll();
        
        // XSSFWorkbook으로 엑셀 생성 (메모리 폭탄!)
        createExcelWithXSSF(request, allData, context);
    }
    
    @Override
    public DownloadRequest.DownloadType getSupportedType() {
        return DownloadRequest.DownloadType.OLD_WAY;
    }
    
    private void createExcelWithXSSF(DownloadRequest request, List<TestData> allData, ExcelContext context) {
        String filePath = excelBuilder.getDownloadPath(context.getDownloadDirectory(), request.getFileName());
        
        try (XSSFWorkbook workbook = excelBuilder.createXSSFWorkbook()) {
            // 시트 설정 (컬럼 너비 + 헤더)
            Sheet sheet = excelBuilder.setupSheet(workbook, "Test Data");
            CellStyle dataStyle = excelBuilder.createDataStyle(workbook);
            
            int rowIndex = 1;
            for (TestData data : allData) {
                Object[] rowData = {
                    data.getId(),
                    data.getName(),
                    data.getDescription(),
                    data.getValue(),
                    data.getCategory(),
                    data.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                };
                
                excelBuilder.writeDataRow(sheet, rowIndex++, rowData, dataStyle);
            }
            
            // 파일 저장
            excelBuilder.saveWorkbook(workbook, filePath);
            
            // 완료 알림
            String downloadUrl = "/api/download/file/" + request.getFileName();
            DownloadProgress completedProgress = DownloadProgress.completed(request.getRequestId(), downloadUrl);
            context.getProgressWebSocketHandler().sendProgress(request.getRequestId(), completedProgress);
            
        } catch (Exception e) {
            log.error("XSSFWorkbook 파일 생성 실패: {}", request.getRequestId(), e);
            throw new RuntimeException("XSSFWorkbook 파일 생성 실패: " + e.getMessage(), e);
        }
    }
}
