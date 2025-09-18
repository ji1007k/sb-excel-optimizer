package com.performance.excel.service;

import com.performance.excel.dto.DownloadRequest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 간단한 통합 성능 테스트
 * SimplePerformanceTest 기반으로 메모리+시간+GC를 한번에 측정
 */
@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class SimpleIntegratedTest {

    @Autowired
    private ExcelDownloadService excelDownloadService;

    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    // 테스트 결과 레코드
    record TestResult(String method, long responseTime, long memoryUsed, long gcTime, 
                     boolean success, String fileName, Long processTime) {
        
        static TestResult success(String method, long responseTime, long memoryUsed, 
                                long gcTime, String fileName) {
            return new TestResult(method, responseTime, memoryUsed, gcTime, true, fileName, null);
        }
        
        static TestResult successAsync(String method, long responseTime, long memoryUsed, 
                                     long gcTime, String fileName, long processTime) {
            return new TestResult(method, responseTime, memoryUsed, gcTime, true, fileName, processTime);
        }
        
        static TestResult failure(String method, String error) {
            return new TestResult(method, -1, -1, -1, false, error, null);
        }
        
        void printResult() {
            if (success) {
                if (processTime != null) {
                    log.info("✅ {} 완료: 즉시응답 {}ms, 실제처리 {}ms, {}MB, {}ms GC", 
                            method, responseTime, processTime, memoryUsed, gcTime);
                } else {
                    log.info("✅ {} 완료: {}ms, {}MB, {}ms GC, 파일: {}", 
                            method, responseTime, memoryUsed, gcTime, fileName);
                }
            } else {
                log.error("❌ {} 실패: {}", method, fileName);
            }
        }
    }

    @Test
    void 통합_성능_비교() {
        log.info("=== 📊 통합 성능 비교 ===");
        
        List<TestResult> results = new ArrayList<>();
        
        // OLD_WAY 측정
        log.info("\n--- OLD_WAY 측정 ---");
        results.add(measureOldWay());
        cleanupMemory();
        
        // 페이징 측정  
        log.info("\n--- 페이징 측정 ---");
        results.add(measurePaging());
        cleanupMemory();
        
        // 스트리밍 측정
        log.info("\n--- 스트리밍 측정 ---");
        results.add(measureStreaming());
        
        // 전체 결과 출력
        printSummary(results);
    }

    @Test 
    void 처리량_테스트() {
        log.info("=== 🚀 처리량 테스트 ===");
        
//        int requestCount = 3; // 각 방식당 5개씩
        
        // OLD_WAY 처리량 측정 -> 1개도 OOM
//        log.info("\n--- OLD_WAY 처리량 ---");
//        measureSyncThroughput("OLD_WAY", 2, (suffix) -> {
//            return excelDownloadService.processOldWayDirectly("test", "oldway-" + suffix);
//        });
        
//        cleanupMemory();
        
        // 페이징 처리량 측정 -> 3개 처리에 21분 소요
//        log.info("\n--- 페이징 처리량 ---");
//        measureSyncThroughput("페이징", 5, (suffix) -> {
//            return excelDownloadService.processPagingDirectly("test", "paging-" + suffix);
//        });

        log.info("\n--- 페이징(비동기) 처리량 ---");
        measureAsyncThroughput("페이징", 20, (suffix) -> {
            return excelDownloadService.processPagingDirectly("test", "paging-" + suffix);
        });
        
//        cleanupMemory();
        
        // 스트리밍 처리량 측정 -> 3개 처리 5분 소요
//        log.info("\n--- 스트리밍 처리량 ---");
//        measureStreamingThroughput(10);
    }

    // 동기 방식 처리량 측정 (OLD_WAY, 페이징)
    private void measureSyncThroughput(String method, int requestCount, java.util.function.Function<String, String> processor) {
        log.info("{}개 요청 순차 처리 시작", requestCount);
        
        long startTime = System.currentTimeMillis();
        int successCount = 0;
        
        for (int i = 1; i <= requestCount; i++) {
            try {
                String fileName = processor.apply(String.valueOf(i));
                successCount++;
                log.info("{} {}/{} 완료: {}", method, i, requestCount, fileName);
            } catch (OutOfMemoryError e) {
                log.error("{} {}/{} OOM 발생", method, i, requestCount);
                // OOM 후 메모리 정리
                for (int j = 0; j < 3; j++) {
                    System.gc();
                    try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                }
                break;
            } catch (Exception e) {
                log.error("{} {}/{} 실패: {}", method, i, requestCount, e.getMessage());
            }
        }
        
        long totalTime = System.currentTimeMillis() - startTime;
        double successRate = (double) successCount / requestCount * 100;
        double throughputPerMinute = successCount / (totalTime / (1000.0 * 60.0));
        
        log.info("\n📊 {} 처리량 결과:", method);
        log.info("  총 시간: {}분", String.format("%.1f", totalTime / (1000.0 * 60.0)));
        log.info("  성공: {}개 / {}개", successCount, requestCount);
        log.info("  성공률: {}%", String.format("%.1f", successRate));
        log.info("  분당 처리량: {}개/분", String.format("%.1f", throughputPerMinute));
    }

    // 비동기 방식 처리량 측정 (OLD_WAY, 페이징)
    private void measureAsyncThroughput(String method, int requestCount, java.util.function.Function<String, String> processor) {
        log.info("{}개 요청 비동기 처리 시작", requestCount);

        long startTime = System.currentTimeMillis();
        AtomicInteger successCount = new AtomicInteger(0);

        // 요청 모으기
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (int i = 1; i <= requestCount; i++) {
            final int idx = i;
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    String fileName = processor.apply(String.valueOf(idx));
                    successCount.incrementAndGet();
                    log.info("{} {}/{} 완료: {}", method, idx, requestCount, fileName);
                } catch (OutOfMemoryError e) {
                    log.error("{} {}/{} OOM 발생", method, idx, requestCount);
                    for (int j = 0; j < 3; j++) {
                        System.gc();
                        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
                    }
                } catch (Exception e) {
                    log.error("{} {}/{} 실패: {}", method, idx, requestCount, e.getMessage());
                }
            });
            futures.add(future);
        }

        // 모든 작업 완료까지 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long totalTime = System.currentTimeMillis() - startTime;
        double successRate = (double) successCount.get() / requestCount * 100;
        double throughputPerMinute = successCount.get() / (totalTime / (1000.0 * 60.0));

        log.info("\n📊 {} 처리량 결과:", method);
        log.info("  총 시간: {}분", String.format("%.1f", totalTime / (1000.0 * 60.0)));
        log.info("  성공: {}개 / {}개", successCount.get(), requestCount);
        log.info("  성공률: {}%", String.format("%.1f", successRate));
        log.info("  분당 처리량: {}개/분", String.format("%.1f", throughputPerMinute));
    }


    // 스트리밍 처리량 측정 (비동기)
    private void measureStreamingThroughput(int requestCount) {
        // 카운터 리셋
        excelDownloadService.getDownloadQueue().resetCounters();
        
        log.info("{}개 요청 전송 시작", requestCount);
        
        long startTime = System.currentTimeMillis();
        
        // 요청 전송
        for (int i = 1; i <= requestCount; i++) {
            excelDownloadService.requestDownload(
                DownloadRequest.DownloadType.STREAMING, 
                "test", "streaming-" + i);
            log.info("요청 {}/{} 전송 완료", i, requestCount);
            
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }
        
        // 20분 대기하며 진행상황 확인
        long endTime = System.currentTimeMillis() + (20 * 60 * 1000);
        int lastCompleted = 0;
        
        while (System.currentTimeMillis() < endTime) {
            var status = excelDownloadService.getQueueStatus();
            int completed = status.getSuccessCount() + status.getFailedCount();
            
            if (completed >= requestCount) {
                log.info("모든 요청 처리 완료!");
                break;
            }
            
            if (completed > lastCompleted) {
                lastCompleted = completed;
                log.info("진행: {}개 완료 (성공: {}, 실패: {}) / {}개 요청", 
                        completed, status.getSuccessCount(), status.getFailedCount(), requestCount);
            }
            
            try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
        }
        
        // 최종 결과
        long totalTime = System.currentTimeMillis() - startTime;
        var finalStatus = excelDownloadService.getQueueStatus();
        
        log.info("\n📊 스트리밍 처리량 결과:");
        log.info("  총 시간: {}분", String.format("%.1f", totalTime / (1000.0 * 60.0)));
        log.info("  성공: {}개", finalStatus.getSuccessCount());
        log.info("  실패: {}개", finalStatus.getFailedCount());
        log.info("  성공률: {}%", String.format("%.1f", finalStatus.getSuccessRate()));
        log.info("  분당 처리량: {}개/분", 
                String.format("%.1f", finalStatus.getSuccessCount() / (totalTime / (1000.0 * 60.0))));

        // 큐, 카운터 초기화
        excelDownloadService.getDownloadQueue().resetQueue();
        excelDownloadService.getDownloadQueue().resetCounters();
    }

    // OLD_WAY 측정
    private TestResult measureOldWay() {
        try {
            long memoryBefore = memoryBean.getHeapMemoryUsage().getUsed();
            long gcBefore = getTotalGCTime();
            long timeBefore = System.currentTimeMillis();
            
            String fileName = excelDownloadService.processOldWayDirectly("test", UUID.randomUUID().toString());
            
            long timeAfter = System.currentTimeMillis();
            long gcAfter = getTotalGCTime();
            long memoryAfter = memoryBean.getHeapMemoryUsage().getUsed();
            
            long responseTime = timeAfter - timeBefore;
            long memoryUsed = (memoryAfter - memoryBefore) / (1024 * 1024);
            long gcTime = gcAfter - gcBefore;
            
            TestResult result = TestResult.success("OLD_WAY", responseTime, memoryUsed, gcTime, fileName);
            result.printResult();
            return result;
            
        } catch (OutOfMemoryError e) {
            // OOM 후 메모리 정리
            for (int i = 0; i < 3; i++) {
                System.gc();
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            }
            TestResult result = TestResult.failure("OLD_WAY", "OOM 발생");
            result.printResult();
            return result;
        } catch (Exception e) {
            TestResult result = TestResult.failure("OLD_WAY", e.getMessage());
            result.printResult();
            return result;
        }
    }

    // 페이징 측정
    private TestResult measurePaging() {
        try {
            long memoryBefore = memoryBean.getHeapMemoryUsage().getUsed();
            long gcBefore = getTotalGCTime();
            long timeBefore = System.currentTimeMillis();
            
            String fileName = excelDownloadService.processPagingDirectly("test", UUID.randomUUID().toString());
            
            long timeAfter = System.currentTimeMillis();
            long gcAfter = getTotalGCTime();
            long memoryAfter = memoryBean.getHeapMemoryUsage().getUsed();
            
            long responseTime = timeAfter - timeBefore;
            long memoryUsed = (memoryAfter - memoryBefore) / (1024 * 1024);
            long gcTime = gcAfter - gcBefore;
            
            TestResult result = TestResult.success("페이징", responseTime, memoryUsed, gcTime, fileName);
            result.printResult();
            return result;
            
        } catch (Exception e) {
            TestResult result = TestResult.failure("페이징", e.getMessage());
            result.printResult();
            return result;
        }
    }

    // 스트리밍 측정 (비동기)
    private TestResult measureStreaming() {
        try {
            long memoryBefore = memoryBean.getHeapMemoryUsage().getUsed();
            long gcBefore = getTotalGCTime();
            long timeBefore = System.currentTimeMillis();
            
            String requestId = excelDownloadService.requestDownload(
                DownloadRequest.DownloadType.STREAMING, "test", UUID.randomUUID().toString());
            
            long responseTime = System.currentTimeMillis() - timeBefore;
            
            // 실제 처리 완료까지 대기
            long processStart = System.currentTimeMillis();
            while (excelDownloadService.getQueueStatus().getQueueSize() > 0 ||
                   excelDownloadService.getQueueStatus().getProcessingCount() > 0) {
                try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
            }
            long processTime = System.currentTimeMillis() - processStart;
            
            long gcAfter = getTotalGCTime();
            long memoryAfter = memoryBean.getHeapMemoryUsage().getUsed();
            
            long memoryUsed = (memoryAfter - memoryBefore) / (1024 * 1024);
            long gcTime = gcAfter - gcBefore;
            
            TestResult result = TestResult.successAsync("스트리밍", responseTime, memoryUsed, gcTime, requestId, processTime);
            result.printResult();
            return result;
            
        } catch (Exception e) {
            TestResult result = TestResult.failure("스트리밍", e.getMessage());
            result.printResult();
            return result;
        }
    }

    // 전체 결과 요약
    private void printSummary(List<TestResult> results) {
        log.info("\n📊 ========== 통합 결과 요약 ==========");
        log.info("| 방식 | 응답시간 | 메모리 | GC시간 | 결과 |");
        log.info("|------|----------|--------|--------|------|");
        
        for (TestResult result : results) {
            if (result.success) {
                if (result.processTime != null) {
                    log.info("| {} | 즉시:{}ms/실제:{}ms | {}MB | {}ms | ✅ |",
                            result.method, result.responseTime, result.processTime, 
                            result.memoryUsed, result.gcTime);
                } else {
                    log.info("| {} | {}ms | {}MB | {}ms | ✅ |",
                            result.method, result.responseTime, result.memoryUsed, result.gcTime);
                }
            } else {
                log.info("| {} | - | - | - | ❌ |", result.method);
            }
        }
    }

    // 메모리 정리
    private void cleanupMemory() {
        System.gc();
        try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
    }

    // GC 시간 조회
    private long getTotalGCTime() {
        return ManagementFactory.getGarbageCollectorMXBeans()
            .stream()
            .mapToLong(GarbageCollectorMXBean::getCollectionTime)
            .sum();
    }
}
