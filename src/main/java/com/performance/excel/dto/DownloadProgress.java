package com.performance.excel.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DownloadProgress {
    private String requestId;
    private DownloadStatus status;
    private long totalCount;
    private long processedCount;
    private int progressPercentage;
    private String message;
    private String downloadUrl;
    private String errorMessage;
    
    public enum DownloadStatus {
        QUEUED,       // 큐에서 대기 중
        PROCESSING,   // 처리 중
        COMPLETED,    // 완료
        FAILED        // 실패
    }
    
    // 편의 메서드들
    public static DownloadProgress queued(String requestId) {
        return DownloadProgress.builder()
                .requestId(requestId)
                .status(DownloadStatus.QUEUED)
                .progressPercentage(0)
                .message("다운로드 요청이 큐에 추가되었습니다.")
                .build();
    }
    
    public static DownloadProgress processing(String requestId, long totalCount, long processedCount) {
        int percentage = totalCount > 0 ? (int) ((processedCount * 100) / totalCount) : 0;
        return DownloadProgress.builder()
                .requestId(requestId)
                .status(DownloadStatus.PROCESSING)
                .totalCount(totalCount)
                .processedCount(processedCount)
                .progressPercentage(percentage)
                .message(String.format("Excel 파일 생성 중... (%d/%d)", processedCount, totalCount))
                .build();
    }
    
    public static DownloadProgress completed(String requestId, String downloadUrl) {
        return DownloadProgress.builder()
                .requestId(requestId)
                .status(DownloadStatus.COMPLETED)
                .progressPercentage(100)
                .downloadUrl(downloadUrl)
                .message("Excel 파일 생성이 완료되었습니다.")
                .build();
    }
    
    public static DownloadProgress failed(String requestId, String errorMessage) {
        return DownloadProgress.builder()
                .requestId(requestId)
                .status(DownloadStatus.FAILED)
                .progressPercentage(0)
                .errorMessage(errorMessage)
                .message("Excel 파일 생성에 실패했습니다.")
                .build();
    }
}
