# 커머스 백엔드 — 대규모 트래픽 설계 실습

Kotlin + Spring Boot로 구현한 커머스 서비스입니다.
회원·상품·주문에서 시작해 **동시성 제어 → 캐시 → 결제 연동 → 이벤트 아키텍처 →
대기열 → 실시간 랭킹**까지 단계적으로 확장했습니다.

기능을 늘리는 것보다 **"왜 이 선택인가"를 측정하고 기록하는 것**에 무게를 뒀습니다.
설계 결정 76건, 부하 테스트 결과, EXPLAIN 분석이 저장소에 그대로 남아 있습니다.

> **출처 고지** — Loopers 부트캠프(BE L2 Vol.3) 과제 저장소입니다.
> Gradle 멀티모듈 골격 · `docker-compose` 인프라 · `ApiResponse`/`CoreException` 공통 클래스 ·
> ktlint pre-commit 은 **부트캠프가 제공한 스캐폴드**입니다.
> 그 외 도메인·인프라 연동·테스트·문서·부하 테스트는 직접 작성했습니다
> (139커밋 중 133커밋, 파일 90 → 562).

---

## 측정 결과

말로 하는 주장 대신 재현 가능한 수치만 적었습니다. 각 행의 문서에 조건과 한계까지 있습니다.

### 1. 복합 인덱스 — 브랜드+가격 정렬 조회 **17.6배**

`product` **500만 건**(브랜드 20개 균등 분포) 기준 MySQL 8.0 EXPLAIN 비교.

| 쿼리 | AS-IS | TO-BE | 결과 |
|---|---|---|---|
| Q1 브랜드 + 좋아요 순 | 1.21ms (range + filter) | 1.03ms (ref lookup) | 구조 개선 |
| **Q2 브랜드 + 가격 순** | **13.4ms** | **0.76ms** | **17.6x** |
| Q3 전체 + 좋아요 순 | 0.03ms | 0.05ms | 변경 없음 |

`price`가 균등 분포라 AS-IS는 380행을 스캔해야 했고, TO-BE는 `brand_id + status`로 진입해
정렬 순서대로 20행만 읽습니다. **Q1·Q3는 데이터 분포상 효과가 작다는 점도 문서에 적었습니다.**

→ [`docs/performance/product-index-analysis.md`](docs/performance/product-index-analysis.md)

### 2. 대기열 — 폴링 부하 **67% 감소**

SLO(p99 ≤ 500ms, 에러율 ≤ 0.1%)에서 **Little's Law로 검증 목표 RPS를 역산**했습니다.

- 고정 3초 폴링: 10K 대기자 → **3,333 req/s**
- 순번별 동적 `retryAfter`(2 / 5 / 10초): **~1,080 req/s** → **67% 감소**
- VU 산출: `1000 × 0.05s = 50` → 마진 2x → 100 VUs
- 대기열 100만 명 시 Redis 메모리 ~100MB 산정

→ [`docs/design/07-queue-system-prd.md`](docs/design/07-queue-system-prd.md)

### 3. 랭킹 API k6 부하 테스트

| 지표 | 값 |
|---|---|
| iterations | **886,393** (1,568.7 it/s) |
| 최대 VUs | **1,066** |
| http_req_duration | p95 **490.41ms** · p90 330.56ms · med 45.61ms |
| 실패 | 26 / 886,393 = **0.0029%** |

캐시 스탬피드 방지 singleflight를 커스텀 메트릭으로 따로 측정했습니다.

→ [`k6/`](k6/) · 원시 결과 [`k6/ranking-benchmark-result.json`](k6/ranking-benchmark-result.json)

---

## 무엇을 구현했나

| 단계 | 내용 |
|---|---|
| 회원·인증 | BCrypt, VO 분리, Interceptor + ArgumentResolver, `@MemberAuthenticated`/`@AdminAuthenticated`, Virtual Threads + 커넥션풀 튜닝 |
| 상품·브랜드·좋아요·주문 | DIP 리팩토링(도메인 순수 `data class` ↔ JPA 모델 분리, `PasswordEncryptor` 포트), QueryDSL 전환, 커서 페이지네이션 |
| 동시성 | 재고 **비관적 락**, 쿠폰 **낙관적 락**, 좋아요 **멱등**(unique constraint + `DataIntegrityViolation` catch), 락 전략 비교 테스트 |
| 성능 | Redis 캐시(Caffeine → Redis 전환), 복합 인덱스 + EXPLAIN, `like_count` 배치 집계 |
| 결제 | **`apps/pg-simulator`** 별도 앱 추가, 폴링 스케줄러, 외부 연동 실패 대응 |
| 이벤트 | Event-Command-Handler, **Transactional Outbox + Kafka 멱등 소비**(`kafka_consumed_event`), AOP 유저 행동 로깅, 선착순 쿠폰 비동기 발급, **`modules/event-contract`**로 계약 단일화 |
| 대기열 | Redis Sorted Set, **Lua 스크립트 원자적 토큰 발급**, `SET NX EX` 분산 락 스케줄러, 토큰 검증 인터셉터, Pipeline으로 `activeCount` O(N) round-trip 제거 |
| 랭킹 | Kafka Consumer 실시간 UPSERT(**event-time 버킷팅 + 멱등**), Redis ZSET, 콜드스타트 Score Carry-Over 배치, Spring Batch 주간/월간 집계 + MV, `GET /rankings?period=daily\|weekly\|monthly` |

---

## 설계 결정 기록 — [`DECISIONS.md`](DECISIONS.md) (76건)

각 항목은 **배경 / 선택지 / 판단 / 근거 / 트레이드오프** 형식입니다.
**하지 않기로 한 결정도 이유와 함께 남겼습니다.**

| # | 결정 |
|---|---|
| 1 | Virtual Threads 활성화 — Tomcat `max-threads` 200 → 50 축소 |
| 2 | 회원가입 중복체크: 사전 조회 → **Unique Constraint** |
| 4 | 캐시 백엔드: Caffeine 로컬 캐시 → **Redis** |
| **6** | **DB Read/Write 분리 및 Redis 도입 "보류"** — 규모가 근거를 못 만들어서 |
| D74 | Daily Consumer — **event-time + event-id 멱등성** |

---

## 문서 지도

```
docs/
├── design/           01 요구사항 · 02 시퀀스 · 03 클래스 · 04 ERD
│                     05 아키텍처 · 06 레이어 책임 · 07 대기열 PRD
├── performance/      상품 인덱스 EXPLAIN 분석 · MySQL InnoDB 읽기 최적화
│                     excalidraw/ (쿼리 플랜 다이어그램 4종)
├── ddl/              V1~V13 스키마 변경 이력
├── seminar/          읽기 최적화 발표 자료
└── blog/             글 초안
k6/                   smoke · load · stress · spike · capacity + 랭킹 벤치마크
DECISIONS.md          설계 결정 76건
```

---

## 실행

### 사전 준비

```shell
make init        # pre-commit(ktlint) 설치
```

### 인프라

`local` 프로필에 필요한 인프라를 `docker-compose`로 제공합니다.

```shell
docker-compose -f ./docker/infra-compose.yml up
```

### 모니터링 (Prometheus + Grafana)

애플리케이션 실행 후 **http://localhost:3000** 에서 `admin/admin` 으로 로그인합니다.

```shell
docker-compose -f ./docker/monitoring-compose.yml up
```

---

## 모듈 구조

- **apps** — 실행 가능한 `SpringBootApplication`
- **modules** — 특정 구현·도메인에 의존하지 않는 재사용 configuration
- **supports** — logging, monitoring 등 부가 기능 add-on

```
Root
├── apps
│   ├── 📦 commerce-api
│   ├── 📦 commerce-batch
│   ├── 📦 commerce-streamer
│   └── 📦 pg-simulator        ← 추가 (결제 연동 테스트용)
├── modules
│   ├── 📦 jpa
│   ├── 📦 redis
│   ├── 📦 kafka
│   └── 📦 event-contract      ← 추가 (이벤트 계약 단일화)
└── supports
    ├── 📦 jackson
    ├── 📦 monitoring
    └── 📦 logging
```
