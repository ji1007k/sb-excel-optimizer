package com.performance.excel.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

@Slf4j
@Component
public class TestDataExcelBuilder {
    
    /**
     * SXSSF Workbook 생성 (메모리 효율적)
     */
    public SXSSFWorkbook createSXSSFWorkbook(int rowAccessWindowSize) {
        return new SXSSFWorkbook(rowAccessWindowSize);
    }
    
    /**
     * XSSF Workbook 생성 (메모리 많이 사용)
     */
    public XSSFWorkbook createXSSFWorkbook() {
        return new XSSFWorkbook();
    }
    
    /**
     * 시트 설정 (컬럼 너비 + 헤더)
     */
    public Sheet setupSheet(Workbook workbook, String sheetName) {
        Sheet sheet = workbook.createSheet(sheetName);
        setColumnWidths(sheet);
        createHeader(sheet, workbook);
        return sheet;
    }
    
    /**
     * 엑셀 컬럼 너비 설정
     */
    public void setColumnWidths(Sheet sheet) {
        sheet.setColumnWidth(0, 3000);   // ID
        sheet.setColumnWidth(1, 6000);   // 이름
        sheet.setColumnWidth(2, 8000);   // 설명
        sheet.setColumnWidth(3, 4000);   // 값
        sheet.setColumnWidth(4, 4000);   // 카테고리
        sheet.setColumnWidth(5, 5000);   // 생성일시
    }
    
    /**
     * 엑셀 헤더 생성
     */
    public void createHeader(Sheet sheet, Workbook workbook) {
        Row headerRow = sheet.createRow(0);
        String[] headers = {"ID", "이름", "설명", "값", "카테고리", "생성일시"};
        
        CellStyle headerStyle = createHeaderStyle(workbook);
        
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }
    }
    
    /**
     * 데이터 행 작성
     */
    public void writeDataRow(Sheet sheet, int rowIndex, Object[] data, CellStyle dataStyle) {
        Row row = sheet.createRow(rowIndex);
        
        for (int i = 0; i < data.length; i++) {
            createCell(row, i, data[i], dataStyle);
        }
    }
    
    /**
     * 엑셀 셀 생성
     */
    public void createCell(Row row, int columnIndex, Object value, CellStyle style) {
        Cell cell = row.createCell(columnIndex);
        
        if (value == null) {
            cell.setCellValue("");
        } else if (value instanceof String) {
            cell.setCellValue((String) value);
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else {
            cell.setCellValue(value.toString());
        }
        
        if (style != null) {
            cell.setCellStyle(style);
        }
    }
    
    /**
     * 헤더 스타일 생성
     */
    public CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        setBorders(style);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        return style;
    }
    
    /**
     * 데이터 스타일 생성
     */
    public CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        setBorders(style);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
    
    /**
     * 셀 테두리 설정
     */
    private void setBorders(CellStyle style) {
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
    
    /**
     * 워크북을 파일로 저장
     */
    public void saveWorkbook(Workbook workbook, String filePath) throws IOException {
        // 디렉토리 생성
        File file = new File(filePath);
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            boolean created = parentDir.mkdirs();
            log.info("📁 Directory created: {} (success: {})", parentDir.getAbsolutePath(), created);
        }
        
        // 파일 저장
        try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
            workbook.write(fileOut);
            log.info("📄 Excel file saved to: {}", filePath);
        }
        
        // SXSSF인 경우 임시 파일 정리
        if (workbook instanceof SXSSFWorkbook) {
            ((SXSSFWorkbook) workbook).dispose();
        }
    }
    
    /**
     * 다운로드 디렉토리 경로 반환
     */
    public String getDownloadPath(String downloadDirectory, String fileName) {
        String currentDir = System.getProperty("user.dir");
        
        // 상대 경로인 경우 절대 경로로 변환
        String finalPath;
        if (!downloadDirectory.startsWith("/") && !downloadDirectory.contains(":")) {
            finalPath = currentDir + File.separator + downloadDirectory;
        } else {
            finalPath = downloadDirectory;
        }
        
        // 디렉토리 생성
        File dir = new File(finalPath);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            log.info("📁 Download directory created: {} (success: {})", finalPath, created);
        }
        
        String dirPath = finalPath.endsWith(File.separator) ? finalPath : finalPath + File.separator;
        return dirPath + fileName;
    }
}
