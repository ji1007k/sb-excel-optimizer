package com.performance.excel.config;

import com.performance.excel.dto.DownloadRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RedisConfigTest {

    @Autowired
    private RedisTemplate<String, DownloadRequest> downloadRequestRedisTemplate;

    @Test
    void Redis_연결_테스트() {
        DownloadRequest request = DownloadRequest.builder()
                .requestId(UUID.randomUUID().toString())
                .fileName("test.xlsx")
                .downloadType(DownloadRequest.DownloadType.SXSSF_CURSOR_PAGING)
                .userId("user-" + UUID.randomUUID())
                .build();

        String key = "test:request-" + System.currentTimeMillis();
        downloadRequestRedisTemplate.opsForValue().set(key, request);
        DownloadRequest saved = downloadRequestRedisTemplate.opsForValue().get(key);

        assertThat(saved).isNotNull();
        assertThat(saved.getFileName()).isEqualTo("test.xlsx");

        // 정리
        downloadRequestRedisTemplate.delete(key);
    }
}