package com.performance.excel;

import com.performance.excel.config.AppConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@RequiredArgsConstructor
public class ExcelOptimizerApplication {

    private final AppConfig appConfig;

    public static void main(String[] args) {
        SpringApplication.run(ExcelOptimizerApplication.class, args);
    }

    // 앱 시작 시 자동 초기화
    @PostConstruct
    public void initAppConfigurations() {
        appConfig.init();
    }
}
