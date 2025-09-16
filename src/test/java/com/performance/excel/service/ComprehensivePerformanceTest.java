package com.performance.excel.service;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 4가지 측면의 종합 성능 테스트
 * 1. 메모리 사용량 (Memory Usage)
 * 2. 응답 시간 (Response Time)  
 * 3. 서버 동시성 및 처리량 (Concurrency & Throughput)
 * 4. 파일 생성 실패율 및 안정성 (Failure Rate & Stability)
 */
@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class ComprehensivePerformanceTest {

    @Autowired
    private ExcelDownloadService excelDownloadService;

    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();

    // ========== 1. 메모리 사용량 (Memory Usage) ==========
    
    @Test
    @DisplayName("1. 메모리 사용량 - OLD_WAY vs 페이징")
    void 메모리사용량_테스트() {
        log.info("=== 1. 메모리 사용량 테스트 ===");
        
        // OLD_WAY 피크 메모리 측정
        System.gc();
        long oldWayBefore = memoryBean.getHeapMemoryUsage().getUsed();
        long gcBefore = getTotalGCTime();
        
        excelDownloadService.processOldWayDirectly("test", "memory-oldway");
        
        long oldWayAfter = memoryBean.getHeapMemoryUsage().getUsed();
        long gcAfter = getTotalGCTime();
        long oldWayMemory = oldWayAfter - oldWayBefore;
        long gcTime = gcAfter - gcBefore;
        
        log.info("OLD_WAY - 메모리: {}MB, GC시간: {}ms", 
            oldWayMemory / (1024 * 1024), gcTime);
        
        // 메모리 정리 후 페이징 측정
        System.gc();
        try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
        
        long pagingBefore = memoryBean.getHeapMemoryUsage().getUsed();
        long gcBefore2 = getTotalGCTime();
        
        excelDownloadService.processPagingDirectly("test", "memory-paging");
        
        long pagingAfter = memoryBean.getHeapMemoryUsage().getUsed();
        long gcAfter2 = getTotalGCTime();
        long pagingMemory = pagingAfter - pagingBefore;
        long gcTime2 = gcAfter2 - gcBefore2;
        
        log.info("페이징 - 메모리: {}MB, GC시간: {}ms", 
            pagingMemory / (1024 * 1024), gcTime2);
        
        // 결과
        if (oldWayMemory > pagingMemory) {
            log.info("결과: 페이징이 {}MB 절약", (oldWayMemory - pagingMemory) / (1024 * 1024));
        }
    }

    // ========== 2. 응답 시간 (Response Time) ==========
    
    @Test
    @DisplayName("2. 응답 시간 - 각 전략별 처리 속도")
    void 응답시간_테스트() {
        log.info("=== 2. 응답 시간 테스트 ===");
        
        // OLD_WAY 응답시간
        long start1 = System.currentTimeMillis();
        excelDownloadService.processOldWayDirectly("test", "response-oldway");
        long oldWayTime = System.currentTimeMillis() - start1;
        log.info("OLD_WAY 응답시간: {}ms", oldWayTime);
        
        // 페이징 응답시간
        long start2 = System.currentTimeMillis();
        excelDownloadService.processPagingDirectly("test", "response-paging");
        long pagingTime = System.currentTimeMillis() - start2;
        log.info("페이징 응답시간: {}ms", pagingTime);
        
        // 스트리밍 응답시간 (비동기 - 요청 접수까지 시간)
        long start3 = System.currentTimeMillis();
        excelDownloadService.requestDownload(
            com.performance.excel.dto.DownloadRequest.DownloadType.STREAMING, 
            "test", "response-streaming");

        // 처리중/대기중인 큐가 없을 떄까지 반복
        while (excelDownloadService.getQueueStatus().getQueueSize() > 0 ||
            excelDownloadService.getQueueStatus().getProcessingCount() > 0) {
            log.info("...처리중: {}, 대기중: {}",
                    excelDownloadService.getQueueStatus().getProcessingCount(),
                    excelDownloadService.getQueueStatus().getQueueSize());
        }
        long streamingResponseTime = System.currentTimeMillis() - start3;
        log.info("스트리밍 응답시간: {}ms", streamingResponseTime);
        
        // 결과 비교
        log.info("결과: {}이 가장 빠름", 
            oldWayTime < pagingTime ? "OLD_WAY" : "페이징");
    }

    // ========== 3. 서버 동시성 및 처리량 (Concurrency & Throughput) ==========
    
    @Test
    @DisplayName("3. 동시성 - OLD_WAY 5개 동시 처리")
    void 동시성_OLD_WAY_테스트() {
        log.info("=== 3-1. OLD_WAY 동시성 테스트 ===");
        
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicLong totalTime = new AtomicLong(0);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 1; i <= threadCount; i++) {
            int finalI = i;
            executor.submit(() -> {
                try {
                    long start = System.currentTimeMillis();
                    excelDownloadService.processOldWayDirectly("test", "concurrent-old-" + finalI);
                    long time = System.currentTimeMillis() - start;
                    
                    totalTime.addAndGet(time);
                    successCount.incrementAndGet();
                    log.info("OLD_WAY {}번 성공: {}ms", finalI, time);
                    
                } catch (OutOfMemoryError e) {
                    log.error("OLD_WAY {}번 OOM 발생!", finalI);
                } catch (Exception e) {
                    log.error("OLD_WAY {}번 실패: {}", finalI, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException ignored) {}
        
        long endTime = System.currentTimeMillis();
        
        log.info("OLD_WAY 동시성 결과: {}/{}개 성공, 총시간: {}ms", 
            successCount.get(), threadCount, endTime - startTime);
        
        executor.shutdown();
    }
    
    @Test
    @DisplayName("3. 동시성 - 페이징 10개 동시 처리")
    void 동시성_페이징_테스트() {
        log.info("=== 3-2. 페이징 동시성 테스트 ===");
        
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        
        long startTime = System.currentTimeMillis();
        
        for (int i = 1; i <= threadCount; i++) {
            int finalI = i;
            executor.submit(() -> {
                try {
                    long start = System.currentTimeMillis();
                    excelDownloadService.processPagingDirectly("test", "concurrent-paging-" + finalI);
                    long time = System.currentTimeMillis() - start;
                    
                    successCount.incrementAndGet();
                    log.info("페이징 {}번 성공: {}ms", finalI, time);
                    
                } catch (Exception e) {
                    log.error("페이징 {}번 실패: {}", finalI, e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }
        
        try {
            latch.await();
        } catch (InterruptedException ignored) {}
        
        long endTime = System.currentTimeMillis();
        
        log.info("페이징 동시성 결과: {}/{}개 성공, 총시간: {}ms", 
            successCount.get(), threadCount, endTime - startTime);
        
        executor.shutdown();
    }

    @Test
    @DisplayName("3. 처리량 - OLD_WAY 1분간 처리량 측정")
    void 처리량_OLD_WAY_테스트() {
        log.info("=== 3-3. OLD_WAY 처리량 테스트 (60초) ===");
        
        int testDurationSeconds = 60;
        long endTime = System.currentTimeMillis() + (testDurationSeconds * 1000);
        int processedCount = 0;
        
        while (System.currentTimeMillis() < endTime) {
            try {
                long start = System.currentTimeMillis();
                excelDownloadService.processOldWayDirectly("test", "throughput-old-" + processedCount);
                long time = System.currentTimeMillis() - start;
                
                processedCount++;
                log.info("OLD_WAY {}번째 완료: {}ms", processedCount, time);
                
            } catch (OutOfMemoryError e) {
                log.error("OLD_WAY {}번째에서 OOM 발생! 처리량 테스트 중단", processedCount);
                break;
            } catch (Exception e) {
                log.warn("OLD_WAY {}번째 실패: {}", processedCount, e.getMessage());
                break;
            }
        }
//        3개/60초 (분당 3.0개)
        double throughput = (double) processedCount / testDurationSeconds;
        log.info("OLD_WAY 처리량: {}개/60초 (분당 {}개)", processedCount, throughput * 60);
    }

    @Test
    @DisplayName("3. 처리량 - 페이징(동기) 1분간 처리량 측정")
    void 처리량_페이징_동기_테스트() {
        log.info("=== 3-4. 페이징 처리량 테스트 (60초) ===");
        
        int testDurationSeconds = 60;
        long endTime = System.currentTimeMillis() + (testDurationSeconds * 1000);
        int processedCount = 0;
        
        while (System.currentTimeMillis() < endTime) {
            try {
                long start = System.currentTimeMillis();
                excelDownloadService.processPagingDirectly("test", "throughput-paging-" + processedCount);
                long time = System.currentTimeMillis() - start;
                
                processedCount++;
                log.info("페이징 {}번째 완료: {}ms", processedCount, time);
                
            } catch (Exception e) {
                log.warn("페이징 {}번째 실패: {}", processedCount, e.getMessage());
                break;
            }
        }
//        3개/60초 (분당 3.0개)
        double throughput = (double) processedCount / testDurationSeconds;
        log.info("페이징 처리량: {}개/60초 (분당 {}개)", processedCount, throughput * 60);
    }

    @Test
    @DisplayName("3. 처리량 - 페이징(비동기) 1분간 처리량 측정")
    void 처리량_페이징_비동기_테스트() {
        log.info("=== 3-5. 페이징(비동기) 실제 처리량 테스트 ===");

        AtomicLong completedCount = new AtomicLong();
        AtomicLong requestCount = new AtomicLong();

        // 미리 많은 요청 생성 (100개)
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            long requestId = requestCount.incrementAndGet();

            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                try {
                    excelDownloadService.processPagingDirectly("test", "throughput-" + requestId);
                    completedCount.incrementAndGet();
                } catch (Exception e) {
                    log.warn("요청 {} 실패: {}", requestId, e.getMessage());
                }
            });

            futures.add(future);
        }

        log.info("100개 요청 생성 완료. 60초 후 완료 개수 확인...");

        // 60초 후 완료 개수 확인
        try {
            Thread.sleep(60000); // 60초 대기
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
//        60초 동안 완료된 작업: 14개 (처리량: 14개/분)
        long completed = completedCount.get();
        log.info("60초 동안 완료된 작업: {}개 (처리량: {}개/분)", completed, completed);

        // 나머지 작업들 취소 (선택적)
        futures.forEach(f -> f.cancel(true));
    }

    @Test
    @DisplayName("3. 처리량 - 스트리밍 큐 처리량 측정")
    void 처리량_스트리밍_테스트() {
        log.info("=== 3-6. 스트리밍 큐 처리량 테스트 ===");
        // 30개 요청을 큐에 한번에 넣기
        int requestCount = 30;
        long startTime = System.currentTimeMillis();
        
        for (int i = 1; i <= requestCount; i++) {
            excelDownloadService.requestDownload(
                com.performance.excel.dto.DownloadRequest.DownloadType.STREAMING, 
                "test", "throughput-streaming-" + i);
        }
        
        log.info("{}개 요청 큐에 추가 완료", requestCount);
        
        // 모든 처리 완료까지 대기
        while (true) {
            var status = excelDownloadService.getQueueStatus();
            int processing = status.getProcessingCount();
            int queued = status.getQueueSize();
            
            log.info("처리중: {}, 대기중: {}", processing, queued);
            
            if (processing == 0 && queued == 0) {
                break;
            }
            
            try {
                Thread.sleep(3000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        double throughput = (double) requestCount / (totalTime / 1000.0);

//        20개/39초 (초당 0.5110514884374601개)
//        30개/60초 (초당 0.4970673029128144개)
        log.info("스트리밍 처리량: {}개/{}초 (초당 {}개)",
            requestCount, totalTime / 1000, throughput);
    }

    // ========== 4. 파일 생성 실패율 및 안정성 (Failure Rate & Stability) ==========
    
    @Test
    @DisplayName("4. 안정성 - OLD_WAY 연속 실행 실패율")
    void 안정성_OLD_WAY_테스트() {
        log.info("=== 4-1. OLD_WAY 안정성 테스트 ===");
        
        int totalAttempts = 5;
        int successCount = 0;
        
        for (int i = 1; i <= totalAttempts; i++) {
            try {
                excelDownloadService.processOldWayDirectly("test", "stability-old-" + i);
                successCount++;
                log.info("OLD_WAY {}회 성공", i);
                
            } catch (OutOfMemoryError e) {
                log.error("OLD_WAY {}회 OOM 발생! 테스트 중단", i);
                break;
            } catch (Exception e) {
                log.error("OLD_WAY {}회 실패: {}", i, e.getMessage());
            }
        }
        
        double successRate = (double) successCount / totalAttempts * 100;
        log.info("OLD_WAY 안정성: {}/{}회 성공 ({}%)",
            successCount, totalAttempts, successRate);
    }
    
    @Test
    @DisplayName("4. 안정성 - 페이징 연속 실행 안정성")
    void 안정성_페이징_테스트() {
        log.info("=== 4-2. 페이징 안정성 테스트 ===");
        
        int totalAttempts = 10; // OLD_WAY보다 많이 테스트
        int successCount = 0;
        
        for (int i = 1; i <= totalAttempts; i++) {
            try {
                excelDownloadService.processPagingDirectly("test", "stability-paging-" + i);
                successCount++;
                log.info("페이징 {}회 성공", i);
                
            } catch (Exception e) {
                log.error("페이징 {}회 실패: {}", i, e.getMessage());
            }
        }
        
        double successRate = (double) successCount / totalAttempts * 100;
        log.info("페이징 안정성: {}/{}회 성공 ({}%)",
            successCount, totalAttempts, successRate);
    }

    // 유틸리티 메소드
    private long getTotalGCTime() {
        return ManagementFactory.getGarbageCollectorMXBeans()
            .stream()
            .mapToLong(GarbageCollectorMXBean::getCollectionTime)
            .sum();
    }
}
