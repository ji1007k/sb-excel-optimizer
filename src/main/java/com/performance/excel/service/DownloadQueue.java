package com.performance.excel.service;

import com.performance.excel.dto.DownloadRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class DownloadQueue {
    
    private static final int MAX_CONCURRENT_DOWNLOADS = 3; // 동시 처리 제한
    
    // 인메모리 큐와 처리중 목록
    private final BlockingQueue<DownloadRequest> downloadQueue = new LinkedBlockingQueue<>();
    private final ConcurrentHashMap<String, DownloadRequest> processingRequests = new ConcurrentHashMap<>();

    // 성공/실패 구분 카운터
    private final AtomicInteger successTaskCounter = new AtomicInteger(0);
    private final AtomicInteger failedTaskCounter = new AtomicInteger(0);
    private final AtomicInteger totalTaskCounter = new AtomicInteger(0);

    /**
     * 다운로드 요청을 큐에 추가
     * 20명 동시 요청 시에도 안정적 처리를 위한 큐 시스템
     */
    public boolean enqueue(DownloadRequest request) {
        try {
            // 현재 처리중인 작업 수 확인
            if (processingRequests.size() >= MAX_CONCURRENT_DOWNLOADS) {
                log.warn("Maximum concurrent downloads reached. Request queued: {}", request.getRequestId());
            }
            
            downloadQueue.offer(request);
            log.info("Download request enqueued: {}", request.getRequestId());
            return true;
        } catch (Exception e) {
            log.error("Failed to enqueue download request: {}", request.getRequestId(), e);
            return false;
        }
    }
    
    /**
     * 큐에서 다음 작업 가져오기 (처리 가능한 경우에만)
     * 최대 3개까지만 동시 처리하여 서버 안정성 확보
     */
    public DownloadRequest dequeue() {
        try {
            // 현재 처리중인 작업 수 확인
            if (processingRequests.size() >= MAX_CONCURRENT_DOWNLOADS) {
                log.debug("Maximum concurrent downloads reached. Waiting...");
                return null;
            }
            
            DownloadRequest request = downloadQueue.poll(1, TimeUnit.SECONDS);
            if (request != null) {
                // 처리중 목록에 추가
                processingRequests.put(request.getRequestId(), request);
                log.info("Download request dequeued: {}", request.getRequestId());
                return request;
            }
        } catch (Exception e) {
            log.error("Failed to dequeue download request", e);
        }
        return null;
    }
    
    /**
     * 성공한 작업 완료 처리 및 목록에서 제거
     */
    public void markCompleted(String requestId) {
        processingRequests.remove(requestId);
        successTaskCounter.incrementAndGet();
        totalTaskCounter.incrementAndGet();
        log.info("Download request completed successfully: {} (Success: {}, Total: {})",
                requestId, successTaskCounter.get(), totalTaskCounter.get());
    }

    /**
     * 실패한 작업 처리 및 목록에서 제거
     */
    public void markFailed(String requestId, String errorMessage) {
        processingRequests.remove(requestId);
        failedTaskCounter.incrementAndGet();
        totalTaskCounter.incrementAndGet();
        log.warn("Download request failed: {} - {} (Failed: {}, Total: {})",
                requestId, errorMessage, failedTaskCounter.get(), totalTaskCounter.get());
    }

    /**
     * 성공한 작업 수 조회
     */
    public int getSuccessTaskCount() {
        return successTaskCounter.get();
    }

    /**
     * 실패한 작업 수 조회
     */
    public int getFailedTaskCount() {
        return failedTaskCounter.get();
    }

    /**
     * 전체 완료된 작업 수 조회 (성공 + 실패)
     */
    public int getTotalCompletedTaskCount() {
        return totalTaskCounter.get();
    }

    /**
     * 성공률 계산
     */
    public double getSuccessRate() {
        int total = totalTaskCounter.get();
        return total > 0 ? (double) successTaskCounter.get() / total * 100.0 : 0.0;
    }

    /**
     * 카운터 리셋 (테스트용)
     */
    public void resetCounters() {
        int oldSuccess = successTaskCounter.getAndSet(0);
        int oldFailed = failedTaskCounter.getAndSet(0);
        int oldTotal = totalTaskCounter.getAndSet(0);
        log.info("Task counters reset: Success {} -> 0, Failed {} -> 0, Total {} -> 0",
                oldSuccess, oldFailed, oldTotal);
    }

    public void resetQueue() {
        downloadQueue.clear();
    }

    /**
     * 큐 상태 조회
     */
    public QueueStatus getQueueStatus() {
        return QueueStatus.builder()
                .queueSize(downloadQueue.size())
                .processingCount(processingRequests.size())
                .maxConcurrentDownloads(MAX_CONCURRENT_DOWNLOADS)
                .successCount(successTaskCounter.get())
                .failedCount(failedTaskCounter.get())
                .totalCompletedCount(totalTaskCounter.get())
                .successRate(getSuccessRate())
                .build();
    }
    
    @lombok.Builder
    @lombok.Getter
    public static class QueueStatus {
        private int queueSize;
        private int processingCount;
        private int maxConcurrentDownloads;
        private int successCount;        // 성공 수
        private int failedCount;         // 실패 수
        private int totalCompletedCount; // 전체 완료 수
        private double successRate;      // 성공률
    }
}
