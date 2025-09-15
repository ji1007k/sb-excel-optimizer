package com.performance.excel.controller;

import com.performance.excel.service.TestDataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/test-data")
@RequiredArgsConstructor
@Slf4j
public class TestDataController {
    
    private final TestDataService testDataService;
    
    /**
     * 대용량 테스트 데이터 생성
     * 20명 동시 다운로드 테스트를 위한 충분한 데이터 생성
     */
    @PostMapping("/generate")
    public ResponseEntity<Map<String, Object>> generateTestData(
            @RequestParam(defaultValue = "10000") int count) {
        
        log.info("Test data generation requested: {} records", count);
        
        try {
            // 최대 100만건까지 제한 (메모리 보호)
            if (count > 1_000_000) {
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "최대 100만건까지만 생성 가능합니다."));
            }
            
            long generatedCount = testDataService.generateTestData(count);
            
            return ResponseEntity.ok(Map.of(
                    "generatedCount", generatedCount,
                    "message", String.format("%d건의 테스트 데이터가 생성되었습니다.", generatedCount),
                    "totalCount", testDataService.getTotalCount()
            ));
            
        } catch (Exception e) {
            log.error("Test data generation failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "테스트 데이터 생성 실패: " + e.getMessage()));
        }
    }
    
    /**
     * 현재 테스트 데이터 개수 조회
     */
    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> getTestDataCount() {
        try {
            long totalCount = testDataService.getTotalCount();
            
            return ResponseEntity.ok(Map.of(
                    "totalCount", totalCount,
                    "message", String.format("현재 %d건의 테스트 데이터가 있습니다.", totalCount)
            ));
            
        } catch (Exception e) {
            log.error("Failed to get test data count", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "데이터 개수 조회 실패: " + e.getMessage()));
        }
    }
    
    /**
     * 모든 테스트 데이터 삭제
     */
    @DeleteMapping("/clear")
    public ResponseEntity<Map<String, String>> clearTestData() {
        try {
            long deletedCount = testDataService.clearAllData();
            
            return ResponseEntity.ok(Map.of(
                    "message", String.format("%d건의 테스트 데이터가 삭제되었습니다.", deletedCount)
            ));
            
        } catch (Exception e) {
            log.error("Failed to clear test data", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "데이터 삭제 실패: " + e.getMessage()));
        }
    }
    
    /**
     * 성능 테스트를 위한 데이터 세트 생성
     */
    @PostMapping("/generate-performance-test")
    public ResponseEntity<Map<String, Object>> generatePerformanceTestData() {
        log.info("Performance test data set generation requested");
        
        try {
            // 성능 테스트용 표준 데이터 세트: 10만건
            int testDataCount = 100_000;
            long generatedCount = testDataService.generateTestData(testDataCount);
            
            return ResponseEntity.ok(Map.of(
                    "generatedCount", generatedCount,
                    "message", "성능 테스트용 데이터 세트(10만건)가 생성되었습니다.",
                    "recommendation", "이제 20명 동시 다운로드 테스트를 실행할 수 있습니다.",
                    "totalCount", testDataService.getTotalCount()
            ));
            
        } catch (Exception e) {
            log.error("Performance test data generation failed", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "성능 테스트 데이터 생성 실패: " + e.getMessage()));
        }
    }
}
