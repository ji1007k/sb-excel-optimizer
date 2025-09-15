package com.performance.excel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadRequest {
    private String requestId;
    private String fileName;
    private DownloadType downloadType;
    private String sessionId;
    
    public enum DownloadType {
        STREAMING,    // JDBC 스트리밍 방식 (메모리 최적화)
        PAGING,       // 페이징 방식 (기존 방식)
        FAST_EXCEL    // FastExcel 라이브러리 사용
    }
}
