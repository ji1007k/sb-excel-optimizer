package com.performance.excel.config;

import com.performance.excel.service.RedisDownloadQueue;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class AppConfig {

    private final RedisDownloadQueue redisDownloadQueue;


    /**
     * Redis 설정 초기화
     * - Consumer Group 생성
     * - Pending 메시지 복구
     */
    @PostConstruct  // Spring이 Bean 생성 후 자동으로 1번만 호출 (앱 시작 시)
    public void initRedisConfigurations() {
        log.info("Initializing Redis configurations...");

        redisDownloadQueue.initConsumerGroup();         // Consumer Group 생성
        redisDownloadQueue.initConsumerName();          // Consumer 이름 생성
        redisDownloadQueue.recoverPendingMessages();    // Pending 메시지 복구

        log.info("Redis configurations initialized successfully");
    }

}
