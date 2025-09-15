package com.performance.excel.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
public class AsyncConfig implements AsyncConfigurer {
    
    @Bean("downloadTaskExecutor")
    public ThreadPoolTaskExecutor downloadTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // CPU 집약적 작업 고려한 스레드 풀 설정
        int corePoolSize = Math.max(2, Runtime.getRuntime().availableProcessors() / 2);
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(corePoolSize * 2);
        executor.setQueueCapacity(100);
        
        // 스레드 이름 설정
        executor.setThreadNamePrefix("Download-");
        
        // 거부 정책: 호출자에서 실행 (서버 안정성 확보)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // 종료 시 작업 완료 대기
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        return executor;
    }
    
    @Override
    public Executor getAsyncExecutor() {
        return downloadTaskExecutor();
    }
}
