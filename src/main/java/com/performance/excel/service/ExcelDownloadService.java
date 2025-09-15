package com.performance.excel.service;

import com.performance.excel.dto.DownloadProgress;
import com.performance.excel.dto.DownloadRequest;
import com.performance.excel.entity.TestData;
import com.performance.excel.repository.TestDataRepository;
import com.performance.excel.websocket.ProgressWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelDownloadService {
    
    private final TestDataRepository testDataRepository;
    private final DownloadQueue downloadQueue;
    private final ProgressWebSocketHandler progressWebSocketHandler;
    private final JdbcTemplate jdbcTemplate;
    
    @Qualifier("downloadTaskExecutor")
    private final Executor downloadTaskExecutor;
    
    @Value("${excel.download.directory:downloads/}")
    private String downloadDirectory;

    private static final int BATCH_SIZE = 1000;
    
    /**
     * 다운로드 디렉토리 경로 반환 (Spring 관리)
     */
    private String getDownloadDir() {
        String currentDir = System.getProperty("user.dir");
        String javaClassPath = System.getProperty("java.class.path");
        String userHome = System.getProperty("user.home");

        // 🔍 디버깅 정보 출력
        log.debug("🔍 === 실행 환경 분석 ===");
        log.info("📂 Current Working Directory: {}", currentDir);
        log.debug("📝 Java Class Path: {}", javaClassPath);
        log.debug("🏠 User Home: {}", userHome);
        log.debug("⚙️ Download Directory Setting: {}", downloadDirectory);

        // IDE에서 실행인지 gradle에서 실행인지 확인
        boolean isIdeExecution = javaClassPath.contains("idea") || javaClassPath.contains("intellij");
        boolean isGradleExecution = javaClassPath.contains("gradle");

        log.debug("🖥️ IDE 실행: {}", isIdeExecution);
        log.debug("🐘 Gradle 실행: {}", isGradleExecution);

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
        } else {
            log.info("📁 Download directory exists: {}", finalPath);
        }
        
        String result = finalPath.endsWith(File.separator) ? finalPath : finalPath + File.separator;
        log.info("🎯 최종 다운로드 경로: {}", result);
        log.info("🔍 === 분석 완료 ===");
        
        return result;
    }
    
    /**
     * 다운로드 요청 처리 (큐에 추가)
     */
    public String requestDownload(DownloadRequest.DownloadType downloadType, String userId, String requestId) {
        String fileName = String.format("test_data_%s_%s.xlsx", downloadType.name().toLowerCase(), requestId);
        
        DownloadRequest request = DownloadRequest.builder()
                .requestId(requestId)
                .fileName(fileName)
                .downloadType(downloadType)
                .userId(userId)
                .build();
        
        boolean enqueued = downloadQueue.enqueue(request);
        if (enqueued) {
            DownloadProgress progress = DownloadProgress.queued(requestId);
            try {
                progressWebSocketHandler.sendProgress(userId, progress);
            } catch (Exception e) {
                log.warn("Failed to send queued progress: {}", e.getMessage());
            }
            
            // 큐 처리 시작
            processQueue();
            
            return requestId;
        } else {
            throw new RuntimeException("다운로드 요청을 큐에 추가하는데 실패했습니다.");
        }
    }
    
    /**
     * 동시성 제어가 적용된 큐 처리
     * 최대 3개까지만 동시 실행되도록 제어
     */
    public void processQueue() {
        DownloadRequest request = downloadQueue.dequeue();
        if (request != null) {
            // 스레드풀에 제출하여 비동기 처리
            CompletableFuture.runAsync(() -> {
                try {
                    processDownload(request);
                } catch (Exception e) {
                    log.error("Download processing failed: {}", request.getRequestId(), e);
                    DownloadProgress failedProgress = DownloadProgress.failed(request.getRequestId(), e.getMessage());
                    try {
                        progressWebSocketHandler.sendProgress(request.getUserId(), failedProgress);
                    } catch (Exception wsException) {
                        log.warn("Failed to send failure progress: {}", wsException.getMessage());
                    }
                } finally {
                    downloadQueue.markCompleted(request.getRequestId());
                    // 다음 요청 처리
                    processQueue();
                }
            }, downloadTaskExecutor);
        }
    }
    
    /**
     * 실제 다운로드 처리
     */
    private void processDownload(DownloadRequest request) {
        log.info("Processing download request: {} ({})", request.getRequestId(), request.getDownloadType());
        
        try {
            switch (request.getDownloadType()) {
                case PAGING -> processWithPaging(request);
                case STREAMING -> processWithStreaming(request);
                case FAST_EXCEL -> processWithFastExcel(request);
                default -> throw new IllegalArgumentException("Unsupported download type: " + request.getDownloadType());
            }
        } catch (Exception e) {
            log.error("Failed to process download: {}", request.getRequestId(), e);
            throw new RuntimeException("다운로드 처리 중 오류가 발생했습니다: " + e.getMessage(), e);
        }
    }
    
    /**
     * 페이징 방식: 메모리에 데이터 축적 (기존 방식 - 비교용)
     */
    private void processWithPaging(DownloadRequest request) {
        log.info("Processing with PAGING method: {}", request.getRequestId());
        
        long totalCount = testDataRepository.getTotalCount();
        List<TestData> allData = new ArrayList<>();
        
        int page = 0;
        long processedCount = 0;
        
        while (true) {
            Pageable pageable = PageRequest.of(page, BATCH_SIZE);
            Page<TestData> dataPage = testDataRepository.findAllByOrderById(pageable);
            
            if (dataPage.isEmpty()) {
                break;
            }
            
            // 메모리에 모든 데이터 축적 (문제점!)
            allData.addAll(dataPage.getContent());
            processedCount += dataPage.getContent().size();
            
            // 진행률 업데이트 빈도 조절 (WebSocket 안정성 확보)
            DownloadProgress progress = DownloadProgress.processing(request.getRequestId(), totalCount, processedCount);
            try {
                progressWebSocketHandler.sendProgress(request.getUserId(), progress);
            } catch (Exception e) {
                log.warn("Failed to send progress update: {}", e.getMessage());
            }
            
            page++;
            
            // 시뮬레이션을 위한 지연
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Download interrupted", e);
            }
        }
        
        // 엑셀 파일 생성
        createExcelFile(request, allData, totalCount);
    }
    
    /**
     * 스트리밍 방식: JDBC ResultSet 스트리밍 - 메모리 최적화
     * 진정한 스트리밍으로 메모리에 데이터 축적 없이 처리
     */
    private void processWithStreaming(DownloadRequest request) {
        log.info("Processing with JDBC STREAMING method: {}", request.getRequestId());
        
        long totalCount = testDataRepository.getTotalCount();
        String filePath = getDownloadDir() + request.getFileName();
        
        log.info("📄 파일 저장 예정 경로: {}", filePath);
        log.info("📁 파일이 저장될 디렉토리: {}", new File(filePath).getParent());
        
        try {
            // JDBC ResultSet 기반 스트리밍으로 엑셀 직접 생성
            createExcelWithJdbcStreaming(request, filePath, totalCount);
            
            // 완료 알림 (안전한 WebSocket 전송)
            String downloadUrl = "/api/download/file/" + request.getFileName();
            DownloadProgress completedProgress = DownloadProgress.completed(request.getRequestId(), downloadUrl);
            try {
                progressWebSocketHandler.sendProgress(request.getUserId(), completedProgress);
            } catch (Exception e) {
                log.warn("Failed to send completion progress: {}", e.getMessage());
            }
            
        } catch (Exception e) {
            log.error("JDBC streaming download failed: {}", request.getRequestId(), e);
            throw new RuntimeException("JDBC 스트리밍 다운로드 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * FastExcel 방식 (향후 구현 예정)
     */
    private void processWithFastExcel(DownloadRequest request) {
        // TODO: FastExcel 라이브러리를 이용한 고성능 처리
        throw new UnsupportedOperationException("FastExcel processing not implemented yet");
    }
    
    /**
     * 진정한 청크 스트리밍 - 1000건씩만 메모리에 로드
     * 메모리 사용량을 일정하게 유지하는 핵심 로직
     */
    private void createExcelWithJdbcStreaming(DownloadRequest request, String filePath, long totalCount) throws Exception {
        // 메모리에 10개 행만 유지 (최대 메모리 절약)
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(10)) {
            Sheet sheet = workbook.createSheet("Test Data");
            
            setColumnWidths(sheet);
            createExcelHeader(sheet, workbook);
            CellStyle dataStyle = createDataStyle(workbook);
            
            int currentRow = 1; // 헤더 다음부터
            long processedCount = 0;
            final int CHUNK_SIZE = 1000; // 1000건씩 청크 처리
            
            // 핵심: 전체 데이터를 한번에 로드하지 않고 청크별로 처리
            for (int offset = 0; offset < totalCount; offset += CHUNK_SIZE) {
                String chunkSql = "SELECT id, name, description, value, category, created_at " +
                                 "FROM test_data ORDER BY id LIMIT ? OFFSET ?";
                
                // 청크별로만 메모리에 로드 (1000건씩)
                List<Object[]> chunkData = jdbcTemplate.query(chunkSql, 
                    new Object[]{CHUNK_SIZE, offset}, 
                    (rs, rowNum) -> new Object[]{
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getBigDecimal("value"),
                        rs.getString("category"),
                        rs.getTimestamp("created_at")
                    });
                
                // 청크 데이터를 즉시 Excel에 쓰기
                for (Object[] rowData : chunkData) {
                    Row row = sheet.createRow(currentRow++);
                    
                    createExcelCell(row, 0, rowData[0], dataStyle);
                    createExcelCell(row, 1, rowData[1], dataStyle);
                    createExcelCell(row, 2, rowData[2], dataStyle);
                    createExcelCell(row, 3, rowData[3], dataStyle);
                    createExcelCell(row, 4, rowData[4], dataStyle);
                    createExcelCell(row, 5, ((java.sql.Timestamp)rowData[5]).toLocalDateTime()
                            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), dataStyle);
                    
                    processedCount++;
                    
                    // 진행률 업데이트 빈도 조절 (5000건마다 - WebSocket 부하 줄이기)
                    if (processedCount % 5000 == 0) {
                        DownloadProgress progress = DownloadProgress.processing(
                                request.getRequestId(), totalCount, processedCount);
                        try {
                            progressWebSocketHandler.sendProgress(request.getUserId(), progress);
                        } catch (Exception e) {
                            log.warn("Failed to send progress update: {}", e.getMessage());
                        }
                    }
                }
                
                // 중요: 청크 처리 후 메모리에서 제거
                chunkData.clear();
                
                log.debug("Processed chunk: {}-{} ({} total rows)", offset, offset + CHUNK_SIZE, processedCount);
            }
            
            // 파일 저장
            try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                workbook.write(fileOut);
                log.info("📄 Excel file saved to: {}", filePath);
            }
            
            workbook.dispose(); // 임시 파일 정리
            
            log.info("Streaming excel file created: {} ({} rows)", filePath, processedCount);
        }
    }
    
    /**
     * 엑셀 컬럼 너비 설정
     */
    private void setColumnWidths(Sheet sheet) {
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
    private void createExcelHeader(Sheet sheet, Workbook workbook) {
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
     * 엑셀 셀 생성
     */
    private void createExcelCell(Row row, int columnIndex, Object value, CellStyle style) {
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
        
        cell.setCellStyle(style);
    }
    
    /**
     * 헤더 스타일 생성
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        style.setFont(font);
        
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        return style;
    }
    
    /**
     * 데이터 스타일 생성
     */
    private CellStyle createDataStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        
        return style;
    }
    
    /**
     * 엑셀 파일 생성 (기존 방식용)
     */
    private void createExcelFile(DownloadRequest request, List<TestData> allData, long totalCount) {
        try {
            String filePath = getDownloadDir() + request.getFileName();
            
            // SXSSFWorkbook으로 메모리 효율적 처리
            try (SXSSFWorkbook workbook = new SXSSFWorkbook(100)) {
                Sheet sheet = workbook.createSheet("Test Data");
                
                setColumnWidths(sheet);
                createExcelHeader(sheet, workbook);
                CellStyle dataStyle = createDataStyle(workbook);
                
                int rowIndex = 1;
                for (TestData data : allData) {
                    Row row = sheet.createRow(rowIndex++);
                    
                    createExcelCell(row, 0, data.getId(), dataStyle);
                    createExcelCell(row, 1, data.getName(), dataStyle);
                    createExcelCell(row, 2, data.getDescription(), dataStyle);
                    createExcelCell(row, 3, data.getValue(), dataStyle);
                    createExcelCell(row, 4, data.getCategory(), dataStyle);
                    createExcelCell(row, 5, data.getCreatedAt().format(
                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")), dataStyle);
                }
                
                // 파일 저장
                try (FileOutputStream fileOut = new FileOutputStream(filePath)) {
                    workbook.write(fileOut);
                }
                
                workbook.dispose();
            }
            
            // 완료 알림 (안전한 WebSocket 전송)
            String downloadUrl = "/api/download/file/" + request.getFileName();
            DownloadProgress completedProgress = DownloadProgress.completed(request.getRequestId(), downloadUrl);
            try {
                progressWebSocketHandler.sendProgress(request.getUserId(), completedProgress);
            } catch (Exception e) {
                log.warn("Failed to send completion progress: {}", e.getMessage());
            }
            
        } catch (Exception e) {
            log.error("Excel file creation failed: {}", request.getRequestId(), e);
            throw new RuntimeException("엑셀 파일 생성 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 큐 상태 조회
     */
    public DownloadQueue.QueueStatus getQueueStatus() {
        return downloadQueue.getQueueStatus();
    }
}
