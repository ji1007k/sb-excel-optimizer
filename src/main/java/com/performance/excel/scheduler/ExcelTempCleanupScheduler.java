package com.performance.excel.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;

// 임시 파일 디렉토리 정리 스케줄러
@Component
@Slf4j
public class ExcelTempCleanupScheduler {

    private static final long ONE_HOUR_MS = 3600000L;

    private static final String POI_TMP_DIR = "poifiles";

    @Scheduled(fixedRate = ONE_HOUR_MS) // 1시간마다
    public void cleanupExpiredTempFiles() {
        cleanupTempFiles(ONE_HOUR_MS);
    }

    /**
     * 테스트에서 사용할 수 있도록 public 메서드로 분리
     * @param maxAgeMs 파일 생성 후 경과시간(밀리초)
     */
    public void cleanupTempFiles(long maxAgeMs) {
        log.info("=== POI 임시 파일 정리 시작 ===");
        
        String tempDir = System.getProperty("java.io.tmpdir");  // C:\Users\[사용자]\AppData\Local\Temp\
        File dir = new File(tempDir + POI_TMP_DIR);

        if (!dir.exists() || !dir.isDirectory()) {
            log.warn("임시 디렉토리가 존재하지 않습니다: {}", tempDir);
            return;
        }

        try {
            // poi-sxssf-sheet*.xml 파일들만 찾아서 삭제
            File[] poiFiles = dir.listFiles((file) ->
                    file.getName().startsWith("poi-sxssf-sheet") &&
                            file.getName().endsWith(".xml"));

            if (poiFiles == null) {
                log.debug("임시 디렉토리 읽기 권한이 없습니다: {}", tempDir);
                return;
            }

            int deletedCount = 0;
            long currentTime = System.currentTimeMillis();

            for (File file : poiFiles) {
                try {
                    if (currentTime - file.lastModified() > maxAgeMs) {
                        if (file.delete()) {
                            deletedCount++;
                            log.debug("POI 임시 파일 삭제: {}", file.getName());
                        } else {
                            log.warn("POI 임시 파일 삭제 실패: {}", file.getName());
                        }
                    }
                } catch (SecurityException e) {
                    log.warn("POI 임시 파일 삭제 권한 없음: {} - {}", file.getName(), e.getMessage());
                }
            }

            if (deletedCount > 0) {
                log.info("POI 임시 파일 정리 완료: {}개 삭제 ({})", deletedCount, LocalDateTime.now());
            }

        } catch (SecurityException e) {
            log.error("임시 디렉토리 접근 권한 오류: {}", e.getMessage());
        } catch (Exception e) {
            log.error("POI 임시 파일 정리 중 오류 발생", e);
        } finally {
            log.info("POI 임시 파일 삭제 완료");
        }
    }

    /**
     * 테스트용: 모든 POI 임시 파일 즉시 정리
     */
    public void cleanupAllForTest() {
        cleanupTempFiles(0L);
    }
}
