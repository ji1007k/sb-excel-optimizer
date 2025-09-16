package com.performance.excel.strategy;

import com.performance.excel.repository.TestDataRepository;
import com.performance.excel.websocket.ProgressWebSocketHandler;
import lombok.Builder;
import lombok.Getter;
import org.springframework.jdbc.core.JdbcTemplate;

@Getter
@Builder
public class ExcelContext {
    
    private final TestDataRepository testDataRepository;
    private final ProgressWebSocketHandler progressWebSocketHandler;
    private final JdbcTemplate jdbcTemplate;
    private final String downloadDirectory;
    
    public static ExcelContext of(TestDataRepository testDataRepository,
                                  ProgressWebSocketHandler progressWebSocketHandler,
                                  JdbcTemplate jdbcTemplate,
                                  String downloadDirectory) {
        return ExcelContext.builder()
                .testDataRepository(testDataRepository)
                .progressWebSocketHandler(progressWebSocketHandler)
                .jdbcTemplate(jdbcTemplate)
                .downloadDirectory(downloadDirectory)
                .build();
    }
}
