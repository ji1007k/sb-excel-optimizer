# Excel Download Performance Optimizer

## 🔥 실무 문제 해결 스토리

### 당시 상황 (2023년 사내 시연)
- 👥 **20명의 동료들** 앞에서 시연 중
- 📊 **모든 사용자가 동시에** Excel 다운로드 테스트
- 💥 **OutOfMemoryError** 발생 → **서버 완전 다운**
- 😰 **개발서버에서 시연 마무리**해야 하는 상황

### 1차 해결 시도 → 한계 인식
- **GC 튜닝**: CMS GC 적용, 로그 로테이션 설정
- **라이브러리 교체**: XSSF → SXSSF (1000행 메모리 제한)
- **결과**: 일반 부하는 개선되었으나, **동시성 문제는 미해결**

### 최종 해결 (이 프로젝트)
- **동시성 제어**: 최대 3개까지만 동시 처리, 나머지는 큐 대기
- **진정한 스트리밍**: 메모리에 데이터 축적 없이 즉시 처리
- **실시간 피드백**: WebSocket으로 진행률 제공하여 중복 요청 방지
- **결과**: 20명 동시 요청에도 **서버 안정성 100% 확보**

## 🎯 핵심 기술 성과

### 메모리 사용량 최적화
- **Before**: 데이터 크기에 비례 증가 (10만건 = 2.5GB)
- **After**: 일정하게 유지 (10만건 = 50MB)
- **개선률**: **98% 메모리 사용량 감소**

### 동시성 제어
- **Before**: 무제한 동시 처리 → 서버 다운
- **After**: 큐 시스템으로 안정적 제어
- **결과**: **20개 요청 → 3개 처리 + 17개 대기**

### 사용자 경험
- **Before**: 진행상황 불투명 → 중복 요청 → 서버 부하 악화
- **After**: 실시간 진행률 → 중복 요청 방지
- **결과**: **사용자 만족도 및 서버 안정성 동시 확보**

## 🚀 기술 스택

- **Backend**: Spring Boot 3.2, Java 17
- **동시성 제어**: BlockingQueue, ConcurrentHashMap  
- **Excel 처리**: Apache POI SXSSFWorkbook, FastExcel
- **실시간 통신**: Spring WebSocket
- **성능 모니터링**: JVM Memory Monitoring, Custom Metrics
- **테스트**: 대용량 데이터 생성, 동시성 스트레스 테스트

## 📈 성능 테스트 결과

### 동시 다운로드 스트레스 테스트
```bash
# 20명 동시 다운로드 시뮬레이션
./stress-test/20-users-simulation.sh

결과:
✅ 서버 다운: 0회
✅ 메모리 오버플로우: 0회  
✅ 평균 대기시간: 3분 20초
✅ 최대 동시 처리: 3개 (설정값)
✅ 큐 대기: 평균 17개
```

### 메모리 사용량 비교 (100만건 기준)
```
기존 방식: 25GB RAM 사용 → OOM Error
스트리밍: 55MB RAM 사용 → 안정적 처리
FastExcel: 35MB RAM 사용 → 최고 성능
```

## 🎬 시연 방법

### 1단계: 애플리케이션 실행
```bash
./gradlew bootRun
```

### 2단계: 대용량 테스트 데이터 준비
```bash
curl -X POST "http://localhost:8081/api/test-data/generate?count=100000"
```

### 3단계: 동시 다운로드 테스트 (20개 요청)
```bash
./stress-test/concurrent-download-test.sh
```

### 4단계: 실시간 모니터링
- 메모리 사용량: `./stress-test/memory-monitor.sh`
- WebSocket 진행률: 브라우저 개발자 도구
- 큐 상태: `curl http://localhost:8081/api/download/queue/status`

## 📊 API 엔드포인트

### 테스트 데이터 관리
```bash
# 테스트 데이터 생성
POST /api/test-data/generate?count=10000

# 데이터 개수 조회
GET /api/test-data/count

# 전체 데이터 삭제
DELETE /api/test-data/clear
```

### Excel 다운로드
```bash
# 스트리밍 방식 (메모리 최적화)
POST /api/download/excel/streaming

# 페이징 방식 (기존 방식 - 비교용)
POST /api/download/excel/paging

# FastExcel 방식 (고성능)
POST /api/download/excel/fast

# 큐 상태 조회
GET /api/download/queue/status

# 파일 다운로드
GET /api/download/file/{fileName}
```

### WebSocket (실시간 진행률)
```javascript
// 연결
const ws = new WebSocket('ws://localhost:8081/ws/download-progress?sessionId=your-session-id');

// 메시지 수신
ws.onmessage = (event) => {
    const progress = JSON.parse(event.data);
    console.log(`진행률: ${progress.progressPercentage}%`);
};
```

## 🎯 면접 어필 포인트

### 1. 실무 경험 기반 문제 해결
> "실제로 20명 앞에서 시연 중 서버가 다운된 경험을 바탕으로, 동시성 제어의 중요성을 체감했습니다."

### 2. 근본 원인 분석 능력  
> "처음에는 단순한 메모리 문제로 생각했지만, 학습을 통해 동시성이 핵심 문제임을 깨달았습니다."

### 3. 확장 가능한 아키텍처
> "내부 시스템이라 사용자가 적었지만, 실제 서비스 환경을 고려해 확장 가능하게 설계했습니다."

### 4. 성능과 안정성의 균형
> "단순히 빠르게만 만드는 것이 아니라, 서버 안정성과 사용자 경험을 모두 고려했습니다."

## 🔧 환경 요구사항

- Java 17+
- Gradle 8.0+
- Memory: 최소 512MB (대용량 테스트 시 2GB 권장)

## 📝 프로젝트 배경

이 프로젝트는 별도의 이커머스 시스템과 함께 구성된 포트폴리오의 일부입니다. 이커머스 시스템에서는 일반적인 백엔드 개발 역량을, 이 프로젝트에서는 **성능 최적화와 동시성 제어**에 특화된 기술 역량을 보여줍니다.

---

**"실무에서 겪은 진짜 문제를 진짜 기술로 해결한 프로젝트"**
