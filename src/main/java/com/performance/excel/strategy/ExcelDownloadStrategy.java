package com.performance.excel.strategy;

import com.performance.excel.dto.DownloadRequest;

// TODO 팩토리 패턴 적용
public interface ExcelDownloadStrategy {
    
    /**
     * 엑셀 다운로드 처리
     */
    void process(DownloadRequest request, ExcelContext context);
    
    /**
     * 지원하는 다운로드 타입 반환
     */
    DownloadRequest.DownloadType getSupportedType();
}
