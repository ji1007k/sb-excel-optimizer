package com.performance.excel;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@RequiredArgsConstructor
public class ExcelOptimizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExcelOptimizerApplication.class, args);
    }

}
