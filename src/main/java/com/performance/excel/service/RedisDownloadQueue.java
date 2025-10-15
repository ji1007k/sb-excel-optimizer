package com.performance.excel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.performance.excel.dto.DownloadRequest;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;


/*Producer-Consumer 패턴
    작업 큐 기반 패턴
    Producer(생산자)가 작업을 큐에 넣음
    Consumer(소비자)가 큐에서 작업을 꺼내서 처리
*/
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisDownloadQueue {

    private final RedisTemplate<String, String> redisTemplate;
    @Qualifier("redisObjectMapper") // Spring 4.3+ 부터 필드 레벨 @Qualifier 지원
    private final ObjectMapper objectMapper;

    // 전용 스레드풀 corePoolSize 와 동일하게 설정
    private static final int MAX_CONCURRENT_DOWNLOADS = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
    private static final String STREAM_KEY = "excel:download:queue";
    private static final String CONSUMER_GROUP = "excel-workers";
    private String CONSUMER_NAME;

    // 통계 키
    /*
        대기 중 (Waiting) = Stream 전체 - Pending
        처리 중 (Processing) = Pending
        완료 (Completed) = excel:stats:success
        실패 (Failed) = excel:stats:failed
     */
    private static final String SUCCESS_COUNTER_KEY = "excel:stats:success";
    private static final String FAILED_COUNTER_KEY = "excel:stats:failed";
    private static final String PROCESSING_SET_KEY = "excel:processing";
    private static final String RECORD_ID_MAP_KEY = "excel:record:map";


    /**
     * Consumer Group 초기화 (최초 1번. 앱 서버 재시작에도 안전)
     * Redis가 자동으로 작업을 각 서버로 분배하도록 하기 위한 역할
     */
    public void initConsumerGroup() {
        try {
            if (!redisTemplate.hasKey(STREAM_KEY)) {
                // 1. Stream 생성
                RecordId recordId = redisTemplate.opsForStream().add(STREAM_KEY, Map.of("init", "true"));
                // 2. Consumer Group 생성
                redisTemplate.opsForStream().createGroup(STREAM_KEY, CONSUMER_GROUP);
                // 3. 더미 메시지 삭제
                redisTemplate.opsForStream().delete(STREAM_KEY, recordId);
            } else {
                // 이미 만들어진 Stream에 Consumer Group 생성
                redisTemplate.opsForStream().createGroup(STREAM_KEY, CONSUMER_GROUP);
            }

            log.info("Redis Consumer Group initialized: {}", CONSUMER_GROUP);
        } catch (Exception e) {
            // 이미 Group이 있으면 에러 발생 → 무시하고 진행
            log.warn("Consumer Group already exists: {}", e.getMessage());
        }
    }

    public void initConsumerName() {
        String hostname = System.getenv("HOSTNAME");

        this.CONSUMER_NAME = hostname != null
                ? "worker-" + hostname
                : "worker-local";

        log.info("Consumer initialized: {}", CONSUMER_NAME);
    }


    /**
     * 큐에 작업 추가
     */
    public boolean enqueue(DownloadRequest downloadRequest) {
        try {
            // JSON으로 변환
            String requestJson = objectMapper.writeValueAsString(downloadRequest);

            // Redis Streams에 추가
            redisTemplate.opsForStream().add(
                    StreamRecords.newRecord()
                            .ofStrings(Map.of(
                                    "requestId", downloadRequest.getRequestId(),
                                    "data", requestJson // JSON 형태로 저장
                            ))
                            .withStreamKey(STREAM_KEY)  // "excel:download:queue" 키에 저장
            );

            log.info("Enqueued to Redis: {}", downloadRequest.getRequestId());
            return true;

        } catch (Exception e) {
            log.error("Failed to enqueue: {}", downloadRequest.getRequestId(), e);
            return false;
        }
    }

    /**
     * 큐에서 작업 꺼내기
     */
    public DownloadRequest dequeue() {
        try {
            // 1. 동시 처리 제한 확인
            Long processingCnt = redisTemplate.opsForSet().size(PROCESSING_SET_KEY);
            if (processingCnt != null && processingCnt >= MAX_CONCURRENT_DOWNLOADS) {
                return null;     // 이미 3개 처리 중이면 대기
            }

            /* 2. Consumer Group으로 메시지 읽기
                Stream: excel:download:queue
                ├─ 1234567890-0: {requestId: "req-1", data: "{...JSON...}"}
                ├─ 1234567891-0: {requestId: "req-2", data: "{...JSON...}"}
                └─ 1234567892-0: {requestId: "req-3", data: "{...JSON...}"}
                ---
                1234567890-0 -> Redis가 자동으로 생성한 ID
                ├─ 1234567890: 타임스탬프 (밀리초)
                └─ 0: 같은 밀리초에 여러 개 들어오면 순번 (0, 1, 2...)
             */
            List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                    Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),     // 서버 ID 동적 생성. Redis가 자동으로 작업 분배
                    StreamReadOptions.empty().count(1).block(Duration.ofSeconds(1)),  // 큐가 비어있으면 1초 대기 (폴링)
                    StreamOffset.create(STREAM_KEY, ReadOffset.lastConsumed())
            );

            if (records == null || records.isEmpty()) {
                return null;
            }

            // 3. JSON을 다시 DownloadRequest 객체로 변환
            MapRecord<String, Object, Object> record = records.get(0);
            String recordId = record.getId().getValue();    // Redis가 생성한 ID
            String requestJson = (String) record.getValue().get("data");
            DownloadRequest request = objectMapper.readValue(requestJson, DownloadRequest.class);

            // 4. 처리 중 목록에 추가
            redisTemplate.opsForSet().add(PROCESSING_SET_KEY, request.getRequestId());

            // 5. RecordId 매핑 저장 (requestId → recordId)
            redisTemplate.opsForHash().put(
                    RECORD_ID_MAP_KEY,
                    request.getRequestId(),
                    recordId
            );

            log.info("Dequeued from Redis: {} (RecordId: {})",
                    request.getRequestId(), recordId);

            return request;
        } catch (Exception e) {
            log.error("Failed to dequeue: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 완료 처리
     */
    public void markCompleted(String requestId) {
        String recordId = (String) redisTemplate.opsForHash().get(RECORD_ID_MAP_KEY, requestId);

        if (recordId != null) {
            // ACK (Pending 에서 제거)
            redisTemplate.opsForStream().acknowledge(
                    STREAM_KEY,
                    CONSUMER_GROUP,
                    recordId
            );

            // 1. Stream에서 완전 삭제
            redisTemplate.opsForStream().delete(STREAM_KEY, recordId);
            // 2. RecordId 매핑 삭제
            redisTemplate.opsForHash().delete(RECORD_ID_MAP_KEY, requestId);
        }

        // 3. 처리 중 목록에서 제거
        redisTemplate.opsForSet().remove(PROCESSING_SET_KEY, requestId);
        // 4. 성공 카운터 증가
        redisTemplate.opsForValue().increment(SUCCESS_COUNTER_KEY);

        log.info("Completed and deleted: {}", requestId);
    }

    /**
     * 실패 처리
     */
    public void markFailed(String requestId, String errorMessage) {
        String recordId = (String) redisTemplate.opsForHash().get(RECORD_ID_MAP_KEY, requestId);

        if (recordId != null) {
            // ACK
            redisTemplate.opsForStream().acknowledge(
                    STREAM_KEY,
                    CONSUMER_GROUP,
                    recordId
            );

            // 1. Stream에서 완전 삭제
            redisTemplate.opsForStream().delete(STREAM_KEY, recordId);
            // 2. RecordId 매핑 삭제
            redisTemplate.opsForHash().delete(RECORD_ID_MAP_KEY, requestId);
        }

        // 3. 처리 중 목록에서 제거
        redisTemplate.opsForSet().remove(PROCESSING_SET_KEY, requestId);
        // 4. 실패 카운터 증가
        redisTemplate.opsForValue().increment(FAILED_COUNTER_KEY);
        log.warn("Failed and deleted: {} - {}", requestId, errorMessage);
    }

    /**
     * 서버 재시작 시 Pending 메시지 복구
     * Consumer Group에 남아있는 미완료 작업을 다시 큐에 추가
     */
    public void recoverPendingMessages() {
        try {
            // 1. 현재 Consumer의 Pending 메시지 조회
            PendingMessages pending = redisTemplate.opsForStream().pending(
                    STREAM_KEY,
                    CONSUMER_GROUP, // Redis가 알아서 다시 분배하도록 Group만 지정
//                    Consumer.from(CONSUMER_GROUP, CONSUMER_NAME),
                    Range.unbounded(),
                    Long.MAX_VALUE
            );

            if (pending == null || pending.isEmpty()) {
                log.info("No pending messages to recover");
                return;
            }

            log.warn("Found {} pending messages, recovering...", pending.size());

            // 2. 각 Pending 메시지 처리
            for (PendingMessage msg : pending) {
                String recordId = msg.getId().getValue();

                // 3. 원본 메시지 읽기
                List<MapRecord<String, Object, Object>> records =
                        redisTemplate.opsForStream().range(
                                STREAM_KEY,
                                Range.closed(recordId, recordId)
                        );

                if (!records.isEmpty()) {
                    MapRecord<String, Object, Object> record = records.get(0);
                    String requestJson = (String) record.getValue().get("data");
                    DownloadRequest request = objectMapper.readValue(
                            requestJson, DownloadRequest.class);

                    // 4. 처리 중 목록에서 제거
                    redisTemplate.opsForSet().remove(
                            PROCESSING_SET_KEY, request.getRequestId());

                    // 5. RecordId 매핑 삭제
                    redisTemplate.opsForHash().delete(
                            RECORD_ID_MAP_KEY, request.getRequestId());

                    // 6. 원본 ACK (Pending에서 제거)
                    redisTemplate.opsForStream().acknowledge(
                            STREAM_KEY, CONSUMER_GROUP, recordId);

                    // 7. 원본 삭제
                    redisTemplate.opsForStream().delete(STREAM_KEY, recordId);

                    // 8. 다시 큐에 추가
                    enqueue(request);

                    log.info("Recovered pending message: {}", request.getRequestId());
                }
            }

            log.info("Pending message recovery completed");

        } catch (Exception e) {
            log.error("Failed to recover pending messages", e);
        }
    }

    /**
     * 통계 조회
     */
    public int getSuccessTaskCount() {
        String count = redisTemplate.opsForValue().get(SUCCESS_COUNTER_KEY);
        return count != null ? Integer.parseInt(count) : 0;
    }

    public int getFailedTaskCount() {
        String count = redisTemplate.opsForValue().get(FAILED_COUNTER_KEY);
        return count != null ? Integer.parseInt(count) : 0;
    }

    public int getTotalCompletedTaskCount() {
        return getSuccessTaskCount() + getFailedTaskCount();
    }

    public double getSuccessRate() {
        int total = getTotalCompletedTaskCount();
        return total > 0 ? (double) getSuccessTaskCount() / total * 100.0 : 0.0;
    }

    public void resetCounters() {
        redisTemplate.delete(SUCCESS_COUNTER_KEY);
        redisTemplate.delete(FAILED_COUNTER_KEY);
        log.info("Redis counters reset");
    }


    /**
     * 큐 상태 조회
     */
    public QueueStatus getQueueStatus() {
        Long totalQueueSize = redisTemplate.opsForStream().size(STREAM_KEY);
        Long processingCount = redisTemplate.opsForSet().size(PROCESSING_SET_KEY);

        // 대기 중 = 전체 - 처리 중
        long waitingCount = (totalQueueSize != null ? totalQueueSize : 0)
                - (processingCount != null ? processingCount : 0);

        return QueueStatus.builder()
                .queueSize((int) Math.max(0, waitingCount))
                .processingCount(processingCount != null ? processingCount.intValue() : 0)
                .maxConcurrentDownloads(MAX_CONCURRENT_DOWNLOADS)
                .successCount(getSuccessTaskCount())
                .failedCount(getFailedTaskCount())
                .totalCompletedCount(getTotalCompletedTaskCount())
                .successRate(getSuccessRate())
                .build();
    }

    @Builder
    @Getter
    public static class QueueStatus {
        private int queueSize;
        private int processingCount;
        private int maxConcurrentDownloads;
        private int successCount;
        private int failedCount;
        private int totalCompletedCount;
        private double successRate;
    }
}
