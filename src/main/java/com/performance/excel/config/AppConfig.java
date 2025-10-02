package com.performance.excel.config;

import com.performance.excel.service.RedisDownloadQueue;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class AppConfig {

    private final RedisDownloadQueue redisDownloadQueue;

    // 설정 초기화 (앱 시작 시)
    public void init() {
        // 1. Consumer Group 생성
        redisDownloadQueue.initConsumerGroup();
    }
}
