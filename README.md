# Excel 대용량 다운로드 최적화 프로젝트

## 프로젝트 개요

### 문제 상황
- **실무 경험**: 이전 회사에서 Excel 다운로드 기능 시연 중 20건 동시 요청으로 OOM 발생
- **증상**: FULL GC로 인한 STW(Stop The World) 현상, 다운로드 파일 손상
- **원인**: XSSF의 전체 DOM 메모리 로딩 + 대용량 데이터 일괄 조회

### 해결 목표
- OOM 방지로 안정적인 대용량 Excel 다운로드
- 동시 요청 처리 제한 적용

## 개선 과정

### 1차: 실무 조치 사항
```
XSSF + 전체 SELECT → SXSSF + 1000건 페이징
```
- **XSSF → SXSSF**: 스트리밍 방식으로 메모리 사용량 감소
- **전체 조회 → 페이징**: ROWNUM으로 1000건씩 분할 처리
- **결과**: 20건 동시 요청 OOM 해결 및 FULL GC 미발생

### 2차: 성능 최적화 (포트폴리오)
```
ROWNUM 페이징 → ID 기반 커서 페이징
```
- **문제**: ROWNUM의 COUNT(*) 연산 오버헤드
- **해결**: `WHERE id > ? ORDER BY id LIMIT 1000` 방식
- **효과**: 쿼리 성능 향상, 인덱스 활용도 증가

### 3차: 동시성 제어 (포트폴리오)
```
무제한 동시 처리 → 블로킹큐 + 동시 처리수 제한
```
- **BlockingQueue 도입**: 요청 대기열 관리
- **동시 처리수 제한**: 3개까지만 동시 처리
- **효과**: 시스템 안정성 확보, 메모리 사용량 예측 가능

### 4차: 라이브러리 최적화 (TODO)
```
Apache POI → FastExcel 라이브러리
```
- 더 나은 메모리 효율성과 쓰기 성능 기대

## 성능 개선 결과

### Before (1차 이전)
- **동시 요청 한계**: 20건에서 OOM 발생
- **메모리 사용**: 전체 데이터를 메모리에 로딩
- **안정성**: FULL GC로 인한 서비스 중단

### After (3차 완료)
- **동시 요청 처리**: 큐 대기 방식으로 안정적 처리
- **메모리 사용**: 청크 단위 처리로 일정한 메모리 사용
- **안정성**: OOM 없는 안정적 서비스

## 아키텍처 설계

### 블로킹큐 기반 동시성 제어
```java
@Service
public class ExcelDownloadService {
    private final BlockingQueue<DownloadRequest> downloadQueue;
    private final ThreadPoolExecutor executor;
    
    // 최대 3개까지만 동시 처리
    private static final int MAX_CONCURRENT_DOWNLOADS = 3;
}
```

### ID 기반 커서 페이징
```sql
-- 기존: ROWNUM 방식 (PK 인덱스 비활용)
SELECT * FROM (
    SELECT ROWNUM rn, t.* FROM table t ORDER BY id
) WHERE rn BETWEEN ? AND ?

-- 개선: ID 커서 방식 (PK 인덱스 활용 + 스킵 스캔)
SELECT * FROM table 
WHERE id > ? 
ORDER BY id 
LIMIT 1000
```

### 전략 패턴 적용
```java
// 다양한 Excel 생성 전략을 추상화
public interface ExcelDownloadStrategy {
    String generateExcel(String userId, String requestId);
}

// 1. 기존 방식 (비교 목적)
class OldWayExcelStrategy implements ExcelDownloadStrategy

// 2. 페이징 방식  
class PagingExcelStrategy implements ExcelDownloadStrategy

// 3. 스트리밍 + 큐 방식
class StreamingExcelStrategy implements ExcelDownloadStrategy
```

## 🧪 성능 테스트 설계

### 테스트 시나리오
1. **단일 전략 성능 측정**: 처리 시간, 메모리 사용량
2. **전략별 비교 테스트**: OLD_WAY vs 페이징 vs 스트리밍
3. **동시성 테스트**: 5건 동시 요청 시 OOM 발생 여부
4. **처리량 테스트**: 60초간 처리 가능한 요청 수

### 측정 지표
- **처리 시간**: 요청부터 파일 생성까지 소요 시간
- **메모리 사용량**: 힙 메모리 사용량 변화
- **처리량**: 단위 시간당 처리 요청 수
- **안정성**: OOM 발생 여부

## 핵심 학습 포인트

### 1. 실무 문제 해결 경험
- 실제 운영 환경에서 발생한 OOM 문제 해결
- 단계적 접근으로 안정성 확보 후 성능 최적화

### 2. 메모리 관리
- JVM 힙 메모리 특성 이해
- 스트리밍 처리로 메모리 사용량 제어

### 3. 동시성 제어
- BlockingQueue를 활용한 백프레셀 구현
- 시스템 리소스 보호를 위한 처리량 제한

### 4. 데이터베이스 최적화
- 페이징 쿼리 성능 최적화
- 인덱스 활용을 고려한 커서 기반 페이징

### 5. 아키텍처 설계
- 전략 패턴으로 다양한 구현체 교체 가능
- 확장성과 유지보수성을 고려한 구조

## 향후 개선 계획

1. **외부 메시지큐 도입**: Redis/RabbitMQ로 큐 영속성 확보
2. **FastExcel 라이브러리 적용**: POI보다 더 나은 성능과 메모리 효율성
3. **모니터링 강화**: 실시간 메트릭 수집 및 알림

## 프로젝트 구조

```
excel-optimizer/
├── src/main/java/com/performance/excel/
│   ├── strategy/           # 전략 패턴 구현
│   ├── service/           # 비즈니스 로직
│   ├── util/              # 유틸리티 클래스
│   └── ExcelOptimizerApplication.java
├── src/test/java/
│   └── service/
│       ├── StrategyComparisonTest.java    # 전략별 성능 비교
│       └── BasicPerformanceTest.java      # 기본 성능 테스트
└── docs/
    └── MEMORY_OPTIMIZATION_GUIDE.md      # 메모리 최적화 가이드
```

---

이 프로젝트는 **실무에서 겪은 실제 문제를 해결하고, 이를 더 발전시킨 포트폴리오**입니다. 단순한 기능 구현이 아닌, **문제 분석 → 해결 → 최적화**의 전 과정을 담고 있습니다.