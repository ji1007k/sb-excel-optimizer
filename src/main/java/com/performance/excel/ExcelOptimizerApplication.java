package com.performance.excel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ExcelOptimizerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExcelOptimizerApplication.class, args);
    }
}
