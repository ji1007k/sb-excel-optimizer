package com.performance.excel.controller;

import com.performance.excel.dto.DownloadRequest;
import com.performance.excel.service.DownloadQueue;
import com.performance.excel.service.ExcelDownloadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/download")
@RequiredArgsConstructor
@Slf4j
public class DownloadController {
    
    private final ExcelDownloadService excelDownloadService;
    
    @Value("${excel.download.directory:downloads/}")
    private String downloadDirectory;


    /**
     * 당시 진짜 문제 방식 Excel 다운로드 (동기 처리 - 완성까지 기다려야 함!)
     */
    @PostMapping("/excel/old-way")
    public ResponseEntity<Resource> downloadExcelOldWay(
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        String requestId = UUID.randomUUID().toString();
        String finalUserId = userId;

        log.warn("💥 당시 진짜 문제 방식 - 동기 처리로 완성까지 기다려야 함! 사용자: {}", finalUserId);

        try {
            // 서비스에서 파일명만 받아옴
            String fileName = excelDownloadService.processOldWayDirectly(finalUserId, requestId);
            
            // Controller에서 HTTP 응답 처리
            File file = new File(getDownloadPath() + fileName);
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = new FileSystemResource(file);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
            
        } catch (Exception e) {
            log.error("당시 방식 다운로드 실패: {}", requestId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 페이징 방식 Excel 다운로드 (큐 사용 안함 - 동기 처리)
     */
    @PostMapping("/excel/paging")
    public ResponseEntity<Resource> downloadExcelPaging(
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        String requestId = UUID.randomUUID().toString();
        String finalUserId = userId;

        log.info("Paging download requested - HTTP Session: {}, Request: {}", finalUserId, requestId);

        try {
            // 서비스에서 파일명만 받아옴 (동기 처리)
            String fileName = excelDownloadService.processPagingDirectly(finalUserId, requestId);
            
            // Controller에서 HTTP 응답 처리
            File file = new File(getDownloadPath() + fileName);
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = new FileSystemResource(file);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
            
        } catch (Exception e) {
            log.error("Paging download failed: {}", requestId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 스트리밍 방식 Excel 다운로드 (메모리 최적화)
     */
    @PostMapping("/excel/streaming")
    public ResponseEntity<Map<String, String>> downloadExcelStreaming(
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        String requestId = UUID.randomUUID().toString();
        String finalUserId = userId;

        log.info("Streaming download requested - HTTP Session: {}, Request: {}", finalUserId, requestId);
        
        try {
            String downloadRequestId = excelDownloadService.requestDownload(
                    DownloadRequest.DownloadType.STREAMING, finalUserId, requestId);
            
            return ResponseEntity.ok(Map.of(
                    "requestId", downloadRequestId,
                    "message", "스트리밍 다운로드 요청이 큐에 추가되었습니다.",
                    "type", "STREAMING"
            ));
        } catch (Exception e) {
            log.error("Streaming download request failed: {}", requestId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "다운로드 요청 실패: " + e.getMessage()));
        }
    }
    
    /**
     * FastExcel 방식 다운로드 (향후 구현)
     */
    @PostMapping("/excel/fast")
    public ResponseEntity<Map<String, String>> downloadExcelFast(
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        String requestId = UUID.randomUUID().toString();
        String finalUserId = userId;
        
        log.info("FastExcel download requested - Session: {}, Request: {}", finalUserId, requestId);
        
        try {
            String downloadRequestId = excelDownloadService.requestDownload(
                    DownloadRequest.DownloadType.FAST_EXCEL, finalUserId, requestId);
            
            return ResponseEntity.ok(Map.of(
                    "requestId", downloadRequestId,
                    "message", "FastExcel 다운로드 요청이 큐에 추가되었습니다.",
                    "type", "FAST_EXCEL"
            ));
        } catch (Exception e) {
            log.error("FastExcel download request failed: {}", requestId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "다운로드 요청 실패: " + e.getMessage()));
        }
    }
    
    /**
     * 완성된 Excel 파일 다운로드
     */
    @GetMapping("/file/{fileName}")
    public ResponseEntity<Resource> downloadFile(@PathVariable String fileName) {
        try {
            // 보안: 파일명 검증
            if (!isValidFileName(fileName)) {
                return ResponseEntity.badRequest().build();
            }
            
            File file = new File(getDownloadPath() + fileName);
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = new FileSystemResource(file);
            
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);
                    
        } catch (Exception e) {
            log.error("File download failed: {}", fileName, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * 다운로드 큐 상태 조회
     */
    @GetMapping("/queue/status")
    public ResponseEntity<DownloadQueue.QueueStatus> getQueueStatus() {
        try {
            DownloadQueue.QueueStatus status = excelDownloadService.getQueueStatus();
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Failed to get queue status", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * 파일명 보안 검증
     */
    private boolean isValidFileName(String fileName) {
        return fileName != null 
                && fileName.matches("^[a-zA-Z0-9._-]+\\.(xlsx|xls)$")
                && !fileName.contains("..")
                && !fileName.contains("/")
                && !fileName.contains("\\");
    }
    
    /**
     * 다운로드 경로 반환
     */
    private String getDownloadPath() {
        if (!downloadDirectory.startsWith("/") && !downloadDirectory.contains(":")) {
            String currentDir = System.getProperty("user.dir");
            String fullPath = currentDir + File.separator + downloadDirectory;
            return fullPath.endsWith(File.separator) ? fullPath : fullPath + File.separator;
        }
        return downloadDirectory;
    }
}
