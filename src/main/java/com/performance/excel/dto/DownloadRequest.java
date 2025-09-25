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
        OLD_WAY,      // 초기 구현 방식: POI(XSSF) + 전체조회 (재현용)
        PAGING,       // 1차 개선: POI(SXSSF) + ROWNUM 기반 페이징
        ASYNC_QUEUE,  // 2차 개선: 비동기 큐 (SXSSF + Blocking Queue), ID 기반 커서 페이징
        EASY_EXCEL,   // 3차 개선1: EasyExcel 라이브러리
        FAST_EXCEL    // 3차 개선2: FastExcel 라이브러리
    }
}
