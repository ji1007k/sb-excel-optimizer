package com.performance.excel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.performance.excel.dto.DownloadRequest;
import com.performance.excel.websocket.ProgressWebSocketHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisMessageListenerContainer redisContainer(
            RedisConnectionFactory connectionFactory,
            ProgressWebSocketHandler handler) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(handler, new ChannelTopic("excel:progress"));
        return container;
    }

    /**
     * DownloadRequest 객체를 Redis에 저장하기 위한 RedisTemplate
     * Jackson2JsonRedisSerializer로 직렬화하여 JSON 형태로 저장
     */
    @Bean
    public RedisTemplate<String, DownloadRequest> downloadRequestRedisTemplate(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, DownloadRequest> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 직렬화
        //  - Key를 String으로 직렬화
        template.setKeySerializer(new StringRedisSerializer());

        //  - Value를 JSON으로 직렬화 (해당 객체 기본 생성자 필요)
        Jackson2JsonRedisSerializer<DownloadRequest> valueSerializer =
                new Jackson2JsonRedisSerializer<>(DownloadRequest.class);
        template.setValueSerializer(valueSerializer);

        return template;
    }

    // Redis 전용 ObjectMapper
    @Bean("redisObjectMapper")
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

}
