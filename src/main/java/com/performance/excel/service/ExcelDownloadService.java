package com.performance.excel.service;

import com.performance.excel.dto.DownloadProgress;
import com.performance.excel.dto.DownloadRequest;
import com.performance.excel.repository.TestDataRepository;
import com.performance.excel.strategy.ExcelContext;
import com.performance.excel.strategy.ExcelDownloadStrategy;
import com.performance.excel.websocket.ProgressWebSocketHandler;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelDownloadService {

    private final TestDataRepository testDataRepository;
    @Getter
    private final DownloadQueue downloadQueue;
    private final ProgressWebSocketHandler progressWebSocketHandler;
    private final JdbcTemplate jdbcTemplate;
    private final List<ExcelDownloadStrategy> strategies;

    @Qualifier("downloadTaskExecutor")
    private final Executor downloadTaskExecutor;

    @Value("${excel.download.directory:downloads/}")
    private String downloadDirectory;

    // 전략 매핑 (초기화 시 생성)
    private Map<DownloadRequest.DownloadType, ExcelDownloadStrategy> strategyMap;

    /**
     * 초기화 시 전략 매핑 생성
     */
    @PostConstruct
    public void init() {
        strategyMap = strategies.stream()
                .collect(Collectors.toMap(
                    ExcelDownloadStrategy::getSupportedType,
                    Function.identity()
                ));
        log.info("Excel download strategies initialized: {}", strategyMap.keySet());
    }

    /**
     * 초기 구현 방식: 큐 없이 바로 처리! (동기 처리)
     */
    public String processOldWayDirectly(String userId, String requestId) {
        log.info("초기 구현 방식 처리 시작 - 동기 처리로 완성까지 기다려야 함!");

        try {
            DownloadRequest request = DownloadRequest.builder()
                    .requestId(requestId)
                    .fileName("basic_method_" + requestId + ".xlsx")
                    .downloadType(DownloadRequest.DownloadType.OLD_WAY)
                    .userId(userId)
                    .build();

            // 전략 패턴 적용
            processWithStrategy(request);

            return request.getFileName();

        } catch (Exception e) {
            log.error("초기 구현 방식 처리 실패: {}", requestId, e);
            throw new RuntimeException("초기 구현 방식 처리 실패: " + e.getMessage(), e);
        }
    }

    /**
     * 페이징 방식: 큐 없이 바로 처리! (동기 처리)
     */
    public String processPagingDirectly(String userId, String requestId) {
        log.info("페이징 방식 처리 시작 - 동기 처리로 완성까지 기다려야 함!");

        try {
            DownloadRequest request = DownloadRequest.builder()
                    .requestId(requestId)
                    .fileName("paging_method_" + requestId + ".xlsx")
                    .downloadType(DownloadRequest.DownloadType.PAGING)
                    .userId(userId)
                    .build();

            // 전략 패턴 적용
            processWithStrategy(request);

            return request.getFileName();

        } catch (Exception e) {
            log.error("페이징 방식 처리 실패: {}", requestId, e);
            throw new RuntimeException("페이징 방식 처리 실패: " + e.getMessage(), e);
        }
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
     */
    public void processQueue() {
        DownloadRequest request = downloadQueue.dequeue();
        if (request != null) {
            // 스레드풀에 제출하여 비동기 처리
            CompletableFuture.runAsync(() -> {
                try {
                    processWithStrategy(request);
                    // 성공 수 카운팅
                    downloadQueue.markCompleted(request.getRequestId());
                } catch (Exception e) {
                    log.error("Download processing failed: {}", request.getRequestId(), e);

                    // 실패 수 카운팅
                    downloadQueue.markFailed(request.getRequestId(), e.getMessage());

                    // WebSocket으로 실패 알림
                    DownloadProgress failedProgress = DownloadProgress.failed(request.getRequestId(), e.getMessage());
                    try {
                        progressWebSocketHandler.sendProgress(request.getUserId(), failedProgress);
                    } catch (Exception wsException) {
                        log.warn("Failed to send failure progress: {}", wsException.getMessage());
                    }
                } finally {
                    // 다음 요청 처리
                    processQueue();
                }
            }, downloadTaskExecutor);
        }
    }

    /**
     * 전략 패턴을 사용한 다운로드 처리
     */
    private void processWithStrategy(DownloadRequest request) {
        log.info("Processing download request: {} ({})", request.getRequestId(), request.getDownloadType());

        // 해당 타입에 맞는 전략 선택
        ExcelDownloadStrategy strategy = strategyMap.get(request.getDownloadType());
        if (strategy == null) {
            throw new IllegalArgumentException("Unsupported download type: " + request.getDownloadType());
        }

        // Context 생성
        ExcelContext context = ExcelContext.of(
            testDataRepository,
            progressWebSocketHandler,
            jdbcTemplate,
            downloadDirectory
        );

        // 전략 실행
        strategy.process(request, context);
    }

    /**
     * 큐 상태 조회
     */
    public DownloadQueue.QueueStatus getQueueStatus() {
        return downloadQueue.getQueueStatus();
    }

}
