package com.performance.excel.service;

import com.performance.excel.dto.DownloadRequest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 간단한 성능 비교 테스트
 * - 처리속도(파일 다운로드 로직 처리 시간), 응답속도(클라 요청에 대한 서버 응답 소요 시간)
 * - 10만건 테스트 시 oldway가 더 빠를 수 있음.
 * - PAGING 방식에서 쿼리 오버헤드로 인해 더 느림.
 * - 단, OLDWAY 다중 요청으로 OOM 발생 시 FULL GC(STW)로 인한 소요시간 + 데이터 손실을 생각하면 PAGING 방식이 안정성 측면에서 더 좋음
 */
@SpringBootTest
@ActiveProfiles("test")
@Slf4j
class SimplePerformanceTest {

    @Autowired
    private ExcelDownloadService excelDownloadService;

    @Test
    void oldway_1회실행() {
        log.info("=== OLD_WAY 테스트 ===");
        
        long start = System.currentTimeMillis();
        String result = excelDownloadService.processOldWayDirectly("test", UUID.randomUUID().toString());
        long time = System.currentTimeMillis() - start;
//        30521ms
        log.info("...OLD_WAY 결과: {}ms", time);
    }

    @Test
    void oldway_N회연속() {
        long iterationCount = 5;

        log.info("=== OLD_WAY {}회 연속 실행 ===", iterationCount);

        List<CompletableFuture<String>> futures = new ArrayList<>();

        ExecutorService executorService = Executors.newFixedThreadPool(3);

        StringBuilder sb = new StringBuilder();
        AtomicLong timeAvg = new AtomicLong(0);
        for (int i = 1; i <= iterationCount; i++) {
            int finalI = i;
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                try {
                    long start = System.currentTimeMillis();
                    String result = excelDownloadService.processOldWayDirectly("test", "test-" + finalI);
                    long time = System.currentTimeMillis() - start;

                    sb.append(String.format("...%d 회차: %d ms - 성공", finalI, time))
                            .append("\n");

                    timeAvg.addAndGet(time);

                    return null;

                } catch (OutOfMemoryError e) {
                    sb.append(String.format("...{%d}회차: OOM 발생!", finalI))
                            .append("\n");
                }

                return null;
            }, executorService);

            futures.add(future);
        }

        // 모든 CompletableFuture 완료까지 대기
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        try {
            allOf.get(5, TimeUnit.MINUTES); // 5분 타임아웃
        } catch (Exception e) {
            log.error("동시 실행 중 오류: {}", e.getMessage());
        }

//...{1}회차: {22821}ms - 성공
//...{2}회차: {20751}ms - 성공
//...{3}회차: {16878}ms - 성공
//...{4}회차: {17568}ms - 성공
//...{5}회차: {17457}ms - 성공
        log.info("=== OLD_WAY {}회 완료 ===", iterationCount);
        log.info(sb.toString());
        log.info("...평균 소요 시간: {}ms", timeAvg.get() / iterationCount);
//        19095ms.
    }

    @Test
    void paging_1회실행() {
        log.info("=== PAGING 테스트 ===");

        long start = System.currentTimeMillis();
        String result = excelDownloadService.processPagingDirectly("test", UUID.randomUUID().toString());
        long time = System.currentTimeMillis() - start;
//        21302ms
        log.info("...PAGING 결과: {}ms", time);
    }

    @Test
    void paging_N회연속() {
        long iterationCount = 20;

        log.info("=== PAGING {}회 연속 실행 ===", iterationCount);

        List<CompletableFuture<String>> futures = new ArrayList<>();

        StringBuffer sb = new StringBuffer();
        AtomicLong timeAvg = new AtomicLong();
        for (int i = 1; i <= iterationCount; i++) {
            int finalI = i;
            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                long start = System.currentTimeMillis();
                String result = excelDownloadService.processPagingDirectly("test", "test-" + finalI);
                long time = System.currentTimeMillis() - start;

                sb.append(String.format("...%d 회차: %d ms - 성공", finalI, time))
                        .append("\n");

                timeAvg.addAndGet(time);

                return null;
            });

            futures.add(future);
        }

        CompletableFuture<Void> allOf = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0])
        );

        try {
            allOf.get(5, TimeUnit.MINUTES); // 5분 타임아웃
        } catch (Exception e) {
            log.error("동시 실행 중 오류: {}", e.getMessage());
        }

//...{1}회차: {21432}ms - 성공
//...{2}회차: {18474}ms - 성공
//...{3}회차: {18509}ms - 성공
//...{4}회차: {22958}ms - 성공
//...{5}회차: {19388}ms - 성공
        log.info("=== PAGING {}회 완료 ===", iterationCount);
        log.info(sb.toString());
        log.info("...평균 소요 시간: {}ms", timeAvg.get() / iterationCount);
//        20152ms
    }

    @Test
    void streaming_N회연속() {
        long iterationCount = 5;

        log.info("=== ASYNC_QUEUE {}회 실행 ===", iterationCount);

        // 큐에 삽입
        for (int i=0; i<iterationCount; i++) {
            excelDownloadService.requestDownload(
                    DownloadRequest.DownloadType.ASYNC_QUEUE, "test", UUID.randomUUID().toString());
            
            log.info("...{}번째 요청 등록 완료", i+1);
        }

        long startTime = System.currentTimeMillis();
        while (excelDownloadService.getQueueStatus().getQueueSize() > 0 ||
                excelDownloadService.getQueueStatus().getProcessingCount() > 0) {
            // 작업이 완료될 떄까지 반복
            log.info("...처리중: {}, 대기중: {}, 소요시간: {}ms",
                    excelDownloadService.getQueueStatus().getProcessingCount(),
                    excelDownloadService.getQueueStatus().getQueueSize(),
                    System.currentTimeMillis() - startTime);

            try {
                Thread.sleep(2000); // 2초 대기 추가
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        long endTime = System.currentTimeMillis();
        long time = endTime - startTime;
//        1207ms ~ 3258ms
        log.info("...ASYNC_QUEUE {}회 완료", iterationCount);
        log.info("...평균 소요시간: {}ms", time / iterationCount);
    }

}
