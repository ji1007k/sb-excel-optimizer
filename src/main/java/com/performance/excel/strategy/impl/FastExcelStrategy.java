package com.performance.excel.strategy.impl;

import com.performance.excel.dto.DownloadRequest;
import com.performance.excel.strategy.ExcelContext;
import com.performance.excel.strategy.ExcelDownloadStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FastExcelStrategy implements ExcelDownloadStrategy {
    
    @Override
    public void process(DownloadRequest request, ExcelContext context) {
        // TODO: FastExcel 라이브러리를 이용한 고성능 처리
        throw new UnsupportedOperationException("FastExcel processing not implemented yet");
    }
    
    @Override
    public DownloadRequest.DownloadType getSupportedType() {
        return DownloadRequest.DownloadType.FAST_EXCEL;
    }
}
