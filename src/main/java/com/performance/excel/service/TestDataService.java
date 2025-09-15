package com.performance.excel.service;

import com.performance.excel.entity.TestData;
import com.performance.excel.repository.TestDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class TestDataService {
    
    private final TestDataRepository testDataRepository;
    private final Random random = new Random();
    
    // 테스트 데이터 생성용 샘플 데이터
    private static final String[] CATEGORIES = {
        "전자제품", "의류", "도서", "스포츠", "가구", "화장품", "식품", "완구"
    };
    
    private static final String[] NAME_PREFIXES = {
        "프리미엄", "스탠다드", "베이직", "디럭스", "스페셜", "리미티드", "클래식", "모던"
    };
    
    private static final String[] NAME_SUFFIXES = {
        "에디션", "컬렉션", "시리즈", "모델", "타입", "버전", "스타일", "패키지"
    };
    
    /**
     * 대용량 테스트 데이터 생성
     * 배치 처리로 메모리 효율적 생성
     */
    @Transactional
    public long generateTestData(int count) {
        log.info("Starting test data generation: {} records", count);
        
        long startTime = System.currentTimeMillis();
        int batchSize = 1000; // 배치 크기
        long totalGenerated = 0;
        
        try {
            for (int i = 0; i < count; i += batchSize) {
                int currentBatchSize = Math.min(batchSize, count - i);
                List<TestData> batch = generateBatch(i, currentBatchSize);
                
                testDataRepository.saveAll(batch);
                totalGenerated += currentBatchSize;
                
                // 메모리 정리
                batch.clear();
                
                if (totalGenerated % 10000 == 0) {
                    log.info("Generated {} / {} records", totalGenerated, count);
                }
            }
            
            long endTime = System.currentTimeMillis();
            log.info("Test data generation completed: {} records in {} ms", 
                    totalGenerated, (endTime - startTime));
            
            return totalGenerated;
            
        } catch (Exception e) {
            log.error("Test data generation failed after {} records", totalGenerated, e);
            throw new RuntimeException("테스트 데이터 생성 실패: " + e.getMessage(), e);
        }
    }
    
    /**
     * 배치 단위로 테스트 데이터 생성
     */
    private List<TestData> generateBatch(int startIndex, int batchSize) {
        List<TestData> batch = new ArrayList<>(batchSize);
        
        for (int i = 0; i < batchSize; i++) {
            TestData data = TestData.builder()
                    .name(generateRandomName(startIndex + i))
                    .description(generateRandomDescription(startIndex + i))
                    .value(generateRandomValue())
                    .category(getRandomCategory())
                    .build();
            
            batch.add(data);
        }
        
        return batch;
    }
    
    /**
     * 랜덤 이름 생성
     */
    private String generateRandomName(int index) {
        String prefix = NAME_PREFIXES[random.nextInt(NAME_PREFIXES.length)];
        String suffix = NAME_SUFFIXES[random.nextInt(NAME_SUFFIXES.length)];
        return String.format("%s %s #%06d", prefix, suffix, index + 1);
    }
    
    /**
     * 랜덤 설명 생성
     */
    private String generateRandomDescription(int index) {
        String[] descriptions = {
            "고품질 소재로 제작된 프리미엄 제품입니다.",
            "실용성과 디자인을 모두 만족하는 베스트셀러 상품입니다.",
            "최신 기술이 적용된 혁신적인 제품입니다.",
            "전문가들이 추천하는 신뢰할 수 있는 브랜드입니다.",
            "사용자 만족도가 높은 인기 상품입니다.",
            "경제적이면서도 실용적인 가성비 제품입니다.",
            "친환경 소재를 사용한 지속가능한 제품입니다.",
            "다양한 용도로 활용 가능한 다기능 제품입니다."
        };
        
        String baseDescription = descriptions[random.nextInt(descriptions.length)];
        return String.format("[ID:%06d] %s", index + 1, baseDescription);
    }
    
    /**
     * 랜덤 가격 생성
     */
    private BigDecimal generateRandomValue() {
        // 1,000원 ~ 1,000,000원 사이의 랜덤 가격
        double randomValue = 1000 + (random.nextDouble() * 999000);
        return BigDecimal.valueOf(Math.round(randomValue / 100.0) * 100); // 100원 단위로 반올림
    }
    
    /**
     * 랜덤 카테고리 선택
     */
    private String getRandomCategory() {
        return CATEGORIES[random.nextInt(CATEGORIES.length)];
    }
    
    /**
     * 전체 테스트 데이터 개수 조회
     */
    public long getTotalCount() {
        return testDataRepository.getTotalCount();
    }
    
    /**
     * 모든 테스트 데이터 삭제
     */
    @Transactional
    public long clearAllData() {
        log.info("Starting to clear all test data");
        
        long totalCount = testDataRepository.getTotalCount();
        testDataRepository.deleteAll();
        
        log.info("Cleared {} test data records", totalCount);
        return totalCount;
    }
    
    /**
     * 성능 테스트용 표준 데이터 세트 생성
     * 20명 동시 다운로드 테스트에 적합한 데이터 양
     */
    @Transactional
    public long generatePerformanceTestDataSet() {
        log.info("Generating performance test data set");
        
        // 기존 데이터 삭제
        clearAllData();
        
        // 10만건 생성 (적당한 부하 + 의미있는 테스트)
        return generateTestData(100_000);
    }
    
    /**
     * 스트레스 테스트용 대용량 데이터 세트 생성
     */
    @Transactional
    public long generateStressTestDataSet() {
        log.info("Generating stress test data set");
        
        // 기존 데이터 삭제
        clearAllData();
        
        // 50만건 생성 (고부하 테스트용)
        return generateTestData(500_000);
    }
}
