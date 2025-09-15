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
    private String userId;
    
    public enum DownloadType {
        OLD_WAY,      // 당시 문제 상황: XSSFWorkbook + 전체조회 (재현용)
        STREAMING,    // JDBC 스트리밍 방식 (메모리 최적화)
        PAGING,       // 페이징 방식 (기존 방식)
        FAST_EXCEL    // TODO FastExcel 라이브러리 사용
    }
}
