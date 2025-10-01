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
     * XSSF 전체 로드 Excel 다운로드 (동기 - 완성까지 대기)
     */
    @PostMapping("/excel/xssf-full-load")
    public ResponseEntity<Resource> downloadExcelXssfFullLoad(
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        String requestId = UUID.randomUUID().toString();
        String finalUserId = userId;

        log.warn("XSSF 전체 로드. 사용자: {}", finalUserId);

        try {
            // 서비스에서 파일명만 받아옴
            String fileName = excelDownloadService.processXssfFullLoadDirectly(finalUserId, requestId);
            
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
            log.error("XSSF 전체 로드 다운로드 실패: {}", requestId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * SXSSF OFFSET 페이징 Excel 다운로드 (동기 - 완성까지 대기)
     */
    @PostMapping("/excel/sxssf-offset-paging")
    public ResponseEntity<Resource> downloadExcelSxssfOffsetPaging(
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        String requestId = UUID.randomUUID().toString();
        String finalUserId = userId;

        log.info("SXSSF OFFSET 페이징 다운로드 요청 - 사용자: {}, 요청: {}", finalUserId, requestId);

        try {
            // 서비스에서 파일명만 받아옴 (동기 처리)
            String fileName = excelDownloadService.processSxssfOffsetPagingDirectly(finalUserId, requestId);
            
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
            log.error("SXSSF OFFSET 페이징 다운로드 실패: {}", requestId, e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * SXSSF CURSOR 페이징 Excel 다운로드 (비동기 - 즉시 응답)
     */
    @PostMapping("/excel/sxssf-cursor-paging")
    public ResponseEntity<Map<String, String>> downloadExcelSxssfCursorPaging(
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        String requestId = UUID.randomUUID().toString();
        String finalUserId = userId;

        log.info("SXSSF CURSOR 페이징 다운로드 요청 - 사용자: {}, 요청: {}", finalUserId, requestId);
        
        try {
            String downloadRequestId = excelDownloadService.requestDownload(
                    DownloadRequest.DownloadType.SXSSF_CURSOR_PAGING, finalUserId, requestId);
            
            return ResponseEntity.ok(Map.of(
                    "requestId", downloadRequestId,
                    "message", "SXSSF CURSOR 페이징 다운로드 요청이 큐에 추가되었습니다.",
                    "type", "SXSSF_CURSOR_PAGING"
            ));
        } catch (Exception e) {
            log.error("SXSSF CURSOR 페이징 다운로드 요청 실패: {}", requestId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "다운로드 요청 실패: " + e.getMessage()));
        }
    }
    
    /**
     * EasyExcel 다운로드 (비동기 - 즉시 응답)
     */
    @PostMapping("/excel/easyexcel")
    public ResponseEntity<Map<String, String>> downloadExcelEasyexcel(
            @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        String requestId = UUID.randomUUID().toString();

        log.info("EasyExcel 다운로드 요청 - 사용자: {}, 요청: {}", userId, requestId);
        
        try {
            String downloadRequestId = excelDownloadService.requestDownload(
                    DownloadRequest.DownloadType.EASYEXCEL, userId, requestId);
            
            return ResponseEntity.ok(Map.of(
                    "requestId", downloadRequestId,
                    "message", "EasyExcel 다운로드 요청이 큐에 추가되었습니다.",
                    "type", "EASYEXCEL"
            ));
        } catch (Exception e) {
            log.error("EasyExcel 다운로드 요청 실패: {}", requestId, e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "다운로드 요청 실패: " + e.getMessage()));
        }
    }

    /**
     * FastExcel 다운로드 (비동기 - 즉시 응답)
     */
    @PostMapping("/excel/fastexcel")
    public ResponseEntity<Map<String, String>> downloadExcelFastexcel(
        @RequestHeader(value = "X-User-Id", required = false) String userId
    ) {
        String requestId = UUID.randomUUID().toString();

        log.info("FastExcel 다운로드 요청 - 사용자: {}, 요청: {}", userId, requestId);

        try {
            String downloadRequestId = excelDownloadService.requestDownload(
                    DownloadRequest.DownloadType.FASTEXCEL, userId, requestId);

            return ResponseEntity.ok(Map.of(
                    "requestId", downloadRequestId,
                    "message", "FastExcel 다운로드 요청이 큐에 추가되었습니다.",
                    "type", "FASTEXCEL"
            ));
        } catch (Exception e) {
            log.error("FastExcel 다운로드 요청 실패: {}", requestId, e);
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
            log.error("파일 다운로드 실패: {}", fileName, e);
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
            log.error("큐 상태 조회 실패", e);
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
