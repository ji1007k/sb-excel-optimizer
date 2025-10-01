package com.performance.excel.dto;

import lombok.*;

@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DownloadRequest {
    private String requestId;
    private String fileName;
    private DownloadType downloadType;
    private String userId;

    @RequiredArgsConstructor
    public enum DownloadType {
        /**
         * XSSF 전체 메모리 로드 방식 (동기)
         * - XSSFWorkbook 사용
         * - 전체 데이터를 메모리에 로드
         * - OOM 위험 있음
         */
        XSSF_FULL_LOAD("XSSF 전체 로드(동기)"),

        /**
         * SXSSF OFFSET(ROWNUM) 페이징 방식 (동기)
         * - SXSSFWorkbook 사용
         * - ROWNUM(OFFSET) 기반 페이징
         * - 안정적이지만 쿼리 오버헤드 있음
         */
        SXSSF_OFFSET_PAGING("SXSSF OFFSET 페이징(동기)"),

        /**
         * SXSSF CURSOR 페이징 방식 (비동기) - 권장
         * - SXSSFWorkbook 사용
         * - ID 커서 기반 페이징
         * - BlockingQueue 비동기 처리
         * - 최고 성능
         */
        SXSSF_CURSOR_PAGING("SXSSF CURSOR 페이징(비동기)"),

        /**
         * EasyExcel 라이브러리 방식 (비동기)
         * - 알리바바 EasyExcel 사용
         * - 어노테이션 기반 간편함
         */
        EASYEXCEL("EasyExcel(비동기)"),

        /**
         * FastExcel 라이브러리 방식 (비동기)
         * - POI 의존성 없는 순수 구현
         * - 가벼운 라이브러리
         */
        FASTEXCEL("FastExcel(비동기)");

        @Getter private final String description;
    }
}
