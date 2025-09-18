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
        int corePoolSize = Math.max(2, Runtime.getRuntime().availableProcessors() / 2); // 서버의 CPU 코어 수에 따라 동적으로 결정
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(corePoolSize * 2);  // 스레드가 모두 바쁠 때, 새로운 작업 요청들이 대기하게 되는 큐의 크기
        executor.setQueueCapacity(100);
        
        // 스레드 이름 설정
        executor.setThreadNamePrefix("Download-");
        
        // CallerRunsPolic 거부 정책: 요청을 보낸 스레드가 직접 작업을 처리 (서버 안정성 확보)
        // 큐까지 가득 찼을 때 새로운 작업 요청을 어떻게 처리할지 정의.
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
