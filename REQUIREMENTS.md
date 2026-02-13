# Requirements

감성 이커머스 서비스의 기능 요구사항 및 10K TPS 성능 개선 요구사항 목록입니다.

---

## 프로젝트 개요

좋아요 누르고, 쿠폰 쓰고, 주문 및 결제하는 감성 이커머스.
내가 좋아하는 브랜드의 상품들을 한 번에 담아 주문하고, 유저 행동은 랭킹과 추천으로 연결됩니다.

### 서비스 흐름

1. 사용자가 **회원가입**을 하고
2. 여러 브랜드의 **상품**을 둘러보고, 마음에 드는 상품엔 **좋아요**를 누르고
3. **쿠폰을 발급**받고, 여러 상품을 **한 번에 주문하고 결제**하며
4. 유저의 행동은 모두 기록되고, **랭킹과 추천**으로 확장됩니다.

### 설계 원칙

- 추후 기능 추가/확장에 유연한 구조
- 새 도메인 추가 시 example 패키지의 4-layer 구조를 따름
- 도메인 간 결합 최소화 (향후 서비스 분리 대비)

---

## 기능 요구사항

### 회원 관리 — DONE

#### FEAT-1: 회원가입

| 항목 | 내용 |
|------|------|
| 상태 | DONE |
| 필요 정보 | 로그인 ID, 비밀번호, 이름, 생년월일, 이메일 |
| 비즈니스 규칙 | - 이미 가입된 로그인 ID로는 가입 불가 |
|  | - 각 정보는 포맷에 맞는 검증 필요 (이름, 이메일, 생년월일) |
|  | - 비밀번호는 암호화(BCrypt)하여 저장 |
| 비밀번호 규칙 | - 8~16자의 영문 대소문자, 숫자, 특수문자만 가능 |
|  | - 생년월일은 비밀번호 내에 포함 불가 (YYYYMMDD, YYMMDD, MMDD) |
| 로그인 ID 규칙 | 영문과 숫자만 허용 |
| 응답 | 201 CREATED + 회원 정보 (마스킹 이름 포함) |
| 중복 시 | 409 CONFLICT |

#### FEAT-2: 내 정보 조회

| 항목 | 내용 |
|------|------|
| 상태 | DONE |
| 인증 | `@MemberAuthenticated` + `AuthenticatedMember` (Interceptor가 `X-Loopers-LoginId`/`X-Loopers-LoginPw` 헤더 검증) |
| 반환 정보 | 로그인 ID, 이름(마스킹), 생년월일, 이메일 |
| 이름 마스킹 규칙 | 마스킹 문자 `*` 통일 (예: 홍길동 → 홍*동, 홍길 → 홍*) |
| 응답 | 200 OK + 회원 정보 |
| 인증 실패 시 | 401 UNAUTHORIZED |

#### FEAT-3: 비밀번호 수정

| 항목 | 내용 |
|------|------|
| 상태 | DONE |
| 인증 | `@MemberAuthenticated` + `AuthenticatedMember` (Interceptor가 기존 비밀번호 검증을 대행) |
| 필요 정보 | 새 비밀번호 (기존 비밀번호는 인증 헤더로 Interceptor에서 검증) |
| 비즈니스 규칙 | - 비밀번호 RULE을 따름 |
|  | - 현재 비밀번호와 동일한 비밀번호로 변경 불가 |
|  | - 변경 시 해당 loginId의 인증 캐시(auth-cache) eviction |
| 응답 | 200 OK + 회원 정보 |
| 인증 실패 시 | 401 UNAUTHORIZED (Interceptor에서 차단) |

### 브랜드 — 설계 완료

#### FEAT-4: 브랜드 관리

| 항목 | 내용 |
|------|------|
| 상태 | 설계 완료 (구현 대기) |
| 대고객 API | `GET /api/v1/brands/{brandId}` (비인증) |
| 어드민 API | CRUD: `GET/POST/PUT/DELETE /api-admin/v1/brands` (LDAP 인증) |
| 비즈니스 규칙 | BR-B1: 브랜드 삭제 시 하위 상품 소프트 삭제 캐스케이드 |
| | BR-B2: 브랜드명 중복 허용 (soft delete + UNIQUE 충돌 방지. 브랜드 식별은 PK 기반) |
| 고객/어드민 정보 분리 | 고객: id, name, description, imageUrl |
| | 어드민: + status, createdAt, updatedAt |
| Soft Delete | status=DELETED + deleted_at 병행 |

### 상품 — 설계 완료

#### FEAT-5: 상품 관리

| 항목 | 내용 |
|------|------|
| 상태 | 설계 완료 (구현 대기) |
| 대고객 API | `GET /api/v1/products` (브랜드 필터, 정렬, 페이징), `GET /api/v1/products/{productId}` |
| 어드민 API | CRUD: `GET/POST/PUT/DELETE /api-admin/v1/products` (LDAP 인증) |
| 정렬 옵션 | latest (기본), price_asc, likes_desc |
| 비즈니스 규칙 | BR-P1: 상품 등록 시 브랜드 존재 필수 (Facade에서 검증) |
| | BR-P2: 브랜드는 등록 후 수정 불가 |
| | BR-P3: 가격 0 이상, BR-P4: 재고 0 이상 |
| 고객/어드민 정보 분리 | 고객: id, brandId/brandName, name, description, price, imageUrl, likeCount, soldOut |
| | 어드민: + stockQuantity, status, createdAt, updatedAt |
| 재고 검증 | ProductModel.deductStock()이 불변식 보호 (도메인 모델 내부) |
| 좋아요 수 조회 | product.like_count 컬럼 직접 반환 (배치 갱신) |

### 좋아요 — 설계 완료

#### FEAT-6: 상품 좋아요

| 항목 | 내용 |
|------|------|
| 상태 | 설계 완료 (구현 대기) |
| 대고객 API | `POST/DELETE /api/v1/products/{productId}/likes` (인증), `GET /api/v1/users/{userId}/likes` (인증) |
| 비즈니스 규칙 | BR-L1: 중복 좋아요 불가 (UNIQUE Constraint) |
| | BR-L2: 좋아요 등록/취소 양방향 멱등 (이미 좋아요→성공, 좋아요 없이 취소→성공) |
| | BR-L3: 본인 좋아요 목록만 조회 가능 |
| | BR-L4: 존재하지 않는 상품에 좋아요 불가 (Facade에서 ProductService로 검증) |
| 집계 전략 | product.like_count 컬럼 (DEFAULT 0) + commerce-batch 배치 갱신 (5분 주기, 10K TPS 대응) |
| 페이징 | 좋아요 목록 조회에 페이징 없음 (API 명세대로) |

### 주문 — 설계 완료

#### FEAT-7: 주문

| 항목 | 내용 |
|------|------|
| 상태 | 설계 완료 (구현 대기) |
| 대고객 API | `POST /api/v1/orders` (인증), `GET /api/v1/orders?startAt&endAt` (인증), `GET /api/v1/orders/{orderId}` (인증) |
| 어드민 API | `GET /api-admin/v1/orders` (페이징, LDAP), `GET /api-admin/v1/orders/{orderId}` (LDAP) |
| 비즈니스 규칙 | BR-O1: 재고 확인 후 차감 (ProductModel.deductStock 불변식 보호) |
| | BR-O2: 주문 시점 상품 스냅샷 저장 (productName, productPrice, brandName만 복사) |
| | BR-O3: 최소 1개 상품, BR-O4: 수량 1 이상 |
| | BR-O5: 본인 주문만 조회 (OrderModel.validateOwner 도메인 검증) |
| | BR-O6: 재고 부족 시 주문 실패 |
| 주문 총액 | 비정규화 안 함. `getTotalAmount() = orderItems.sumOf { it.amount }` 파생 계산 |
| 스냅샷 범위 | productName, productPrice, brandName (imageUrl, description 제외) |
| OrderItem 필드 역할 | 참조(productId), 스냅샷(Name/Price/Brand), 주문입력(quantity), 파생(amount) |
| 주문 번호 | UUID (내부 ID 노출 방지) |
| 주문 상태 | ORDERED / CANCELLED (주문 취소 API는 현재 스코프 외) |
| 트랜잭션 | Facade @Transactional로 재고 차감 + 주문 생성 원자성 보장 |
| 동시성 | Phase 1에 비관적 락(SELECT FOR UPDATE) 포함. 재고 차감 시 정합성 보장 |
| 대고객 페이징 | 기간 필터만 적용, 페이징 없음 (API 명세대로) |

### 쿠폰 — TODO

#### FEAT-8: 쿠폰 (예정)

| 항목 | 내용 |
|------|------|
| 상태 | TODO |
| 예상 기능 | 쿠폰 발급, 조회, 사용 처리 |

### 결제 — TODO

#### FEAT-9: 결제 (예정)

| 항목 | 내용 |
|------|------|
| 상태 | TODO |
| 예상 기능 | 주문에 대한 결제 처리 |

### 랭킹/추천 — TODO

#### FEAT-10: 랭킹 및 추천 (예정)

| 항목 | 내용 |
|------|------|
| 상태 | TODO |
| 예상 기능 | 유저 행동 기반 랭킹, 상품 추천 |

---

## 성능 목표

- **목표 TPS**: 10,000 (피크 기준)
- **달성 전략**: 단계적 적용 (Phase 1 → 2 → 3)
- **기준**: 국내 커머스 피크 트래픽 (3,000~10,000+ TPS)

---

## Phase 1: 즉시 적용 (코드 변경 최소) — DONE

### REQ-1.1: Virtual Threads 활성화

| 항목 | 내용 |
|------|------|
| 상태 | DONE |
| 배경 | Platform Thread 200개 제한으로 동시 처리량이 Tomcat thread pool에 의존 |
| 요구사항 | JDK 21 Virtual Threads를 활성화하여 blocking I/O 대기 시 스레드 풀 고갈 방지 |
| 수용 기준 | `spring.threads.virtual.enabled=true` 설정, 기존 테스트 전체 통과 |
| 제약사항 | `synchronized` 사용 금지 (`ReentrantLock` 사용), pinning 방지 |

### REQ-1.2: Tomcat 튜닝

| 항목 | 내용 |
|------|------|
| 상태 | DONE |
| 배경 | 기본 `max-connections=8192`, `accept-count=100`은 10K TPS에 부족 |
| 요구사항 | 동시 연결 수와 대기 큐를 확장하여 피크 트래픽 수용 |
| 수용 기준 | `max-threads=50`, `max-connections=10000`, `accept-count=200` |

### REQ-1.3: DB 커넥션 풀 확장

| 항목 | 내용 |
|------|------|
| 상태 | DONE |
| 배경 | HikariCP `maximum-pool-size=40`은 Virtual Threads 환경에서 부족할 수 있음 |
| 요구사항 | 단일 DB 환경에서 커넥션 풀을 적절히 확장 |
| 수용 기준 | `maximum-pool-size=50` |
| 제약사항 | R/W 분리 전까지 과도한 확장 불필요 |

### REQ-1.4: 회원가입 중복체크 방식 전환

| 항목 | 내용 |
|------|------|
| 상태 | DONE |
| 배경 | `findByLoginId` + `findByEmail` 2회 SELECT 후 중복 판단 → race condition 가능, 불필요한 DB 부하 |
| 요구사항 | DB Unique Constraint로 중복을 보장하고, 예외 처리로 응답 변환 |
| 수용 기준 | - `loginId`, `email` 컬럼에 `unique=true` 적용 |
|  | - 사전 조회 로직 제거 |
|  | - `DataIntegrityViolationException` → 409 CONFLICT 응답 변환 |
|  | - Facade 레이어에서 예외 catch (`@Transactional` 경계 밖) |
| 금지사항 | - `saveAndFlush()` 사용 금지 (Hibernate write-behind 최적화 파괴) |
|  | - Service 내부에서 `DataIntegrityViolationException` catch 금지 |

### REQ-1.5: 인증 헤더 통일 및 인증 처리 중앙화

| 항목 | 내용 |
|------|------|
| 상태 | DONE |
| 배경 | 각 Controller에서 `@RequestHeader`로 인증 헤더를 직접 받아 중복 코드 발생 |
| 요구사항 | Interceptor + ArgumentResolver 패턴으로 인증을 중앙화 |
| 수용 기준 | - `@MemberAuthenticated` 어노테이션으로 인증 필요 API 표시 |
|  | - `AuthenticatedMember` 객체가 Controller 파라미터로 자동 주입 |
|  | - 인증 헤더: `X-Loopers-LoginId` / `X-Loopers-LoginPw` |
|  | - 인증 실패 시 401 UNAUTHORIZED 응답 |
|  | - Controller에서 인증 관련 코드 완전 제거 |
| 제약사항 | JWT 미사용 (의도적 결정) |

---

## Phase 2: 캐싱 및 인프라 확장 — 진행 중

### REQ-2.1: 인증 결과 캐싱 (BCrypt 호출 최소화)

| 항목 | 내용 |
|------|------|
| 상태 | DONE |
| 배경 | 매 인증 요청마다 BCrypt 비교(~100ms) 수행 → 최대 ~2,000 TPS 병목 |
| 요구사항 | Caffeine 로컬 캐시로 인증 결과를 캐싱하여 BCrypt 호출 스킵 |
| 수용 기준 | - `auth-cache`: TTL 5분, max 10,000 엔트리 |
|  | - 캐시 키: `loginId`, 값: `CachedAuth(memberId, loginId, passwordDigest)` |
|  | - SHA256으로 비밀번호 일치 확인 (BCrypt 대신) |
|  | - 캐시 히트 + 비밀번호 일치 시 `memberService.authenticate()` 호출 스킵 |
|  | - 캐시 히트 + 비밀번호 불일치 시 `authenticate()` 재호출 |
|  | - 비밀번호 변경 시 해당 `loginId`의 캐시 eviction |
| 제약사항 | - Redis가 아닌 Caffeine 로컬 캐시 사용 (단일 인스턴스 환경) |
|  | - Spring Cache Abstraction 기반으로 향후 Redis 전환 가능하게 설계 |

### REQ-2.2: 회원 조회 캐싱

| 항목 | 내용 |
|------|------|
| 상태 | 보류 |
| 배경 | `getMember(id)` 조회가 빈번하게 발생, DB 부하 감소 필요 |
| 요구사항 | `member-cache`를 활용하여 회원 조회 결과 캐싱 |
| 수용 기준 | - `member-cache`: TTL 10분, max 10,000 엔트리 |
|  | - 회원 정보 변경 시 캐시 eviction |
| 비고 | 인증 캐시(auth-cache)가 BCrypt 병목을 해소하여 현재 긴급도 낮음. 실측 후 판단 |

### REQ-2.3: DB Read/Write 분리

| 항목 | 내용 |
|------|------|
| 상태 | 보류 |
| 배경 | 단일 DB에 읽기/쓰기가 집중 |
| 요구사항 | `AbstractRoutingDataSource` + MySQL Replica로 읽기 분산 |
| 보류 사유 | 현재 DB가 병목이 아님, 측정 후 판단 |
| 사전 준비 | `@Transactional(readOnly = true)` 이미 적용 완료 |

### REQ-2.4: Redis 캐싱 도입

| 항목 | 내용 |
|------|------|
| 상태 | 보류 |
| 배경 | 멀티 인스턴스 환경에서 캐시 공유 필요 |
| 요구사항 | Caffeine → Redis 전환 |
| 보류 사유 | 현재 단일 인스턴스, Spring Cache Abstraction으로 전환 준비 완료 |

### REQ-2.5: Lettuce 커넥션 풀링

| 항목 | 내용 |
|------|------|
| 상태 | 보류 |
| 배경 | Redis 사용 시 단일 커넥션 멀티플렉싱의 한계 |
| 요구사항 | `LettucePoolingClientConfiguration` 적용 |
| 보류 사유 | Redis 미사용 상태 |

---

## Phase 3: 안정성 강화 — TODO

### REQ-3.1: Circuit Breaker

| 항목 | 내용 |
|------|------|
| 상태 | TODO |
| 요구사항 | Resilience4j 기반 Circuit Breaker 적용 |
| 목적 | 외부 의존성 장애 시 빠른 실패 및 복구 |

### REQ-3.2: Rate Limiting

| 항목 | 내용 |
|------|------|
| 상태 | TODO |
| 요구사항 | API 레벨 요청 제한 |
| 목적 | 악의적 트래픽 및 과부하 방지 |

### REQ-3.3: Graceful Degradation

| 항목 | 내용 |
|------|------|
| 상태 | TODO |
| 요구사항 | 폴백 패턴 적용 |
| 목적 | 부분 장애 시에도 핵심 기능 유지 |

---

## 2주차 설계 공통 결정사항

> 각 결정의 상세 배경·선택지·트레이드오프는 `DECISIONS.md`의 해당 번호를 참조한다.

| 결정 | 내용 | Decision |
|------|------|----------|
| 고객/어드민 Facade 분리 | 전체 도메인에 일괄 적용. Service는 공유, Facade만 분리. 확장성 확보 | D8 |
| 고객/어드민 DTO 분리 | Controller 레벨에서 노출 필드 분리. Facade는 동일 Info 반환 | D15 |
| 재고 검증 위치 | 도메인 모델 내부 (ProductModel.deductStock → 불변식 보호) | D9 |
| 본인 주문 검증 위치 | 도메인 모델 내부 (OrderModel.validateOwner → 비즈니스 규칙 보호) | D10 |
| Soft Delete 방식 | status=DELETED + deleted_at 병행. delete() 메서드에서 동시 설정 | D11 |
| 좋아요 수 집계 | product.like_count 컬럼 (DEFAULT 0) + commerce-batch 배치 갱신 (5분 주기) | D12 |
| 주문 번호 | UUID 방식 (내부 auto-increment ID 노출 방지) | D13 |
| INACTIVE/SUSPENDED 상태 | 미포함 (YAGNI. 현재 요구사항에 비활성화/판매중단 시나리오 없음) | D14 |
| SOLD_OUT 상태 | 제거. ProductStatus = ACTIVE/DELETED. stock=0이 품절 표현. soldOut Boolean 파생 필드로 고객 전달 | D16 |
| 브랜드명 중복 | 허용 (BR-B2 수정). UNIQUE 제거. soft delete 충돌 방지, 브랜드 식별은 PK 기반 | D19 |
| 좋아요/취소 멱등 | 양방향 멱등 (DuplicateKeyException catch / affected rows=0 → 200 OK) | D17 |
| Service 메서드 분리 | 고객용 (ACTIVE only) / 어드민용 (상태 무관) 분리. 확장성 확보 | D18 |
| DELETED 리소스 고객 접근 | 404 반환 (브랜드/상품 동일). 주문은 스냅샷으로 노출 | D21 |
| 좋아요 수 초기화 | product.like_count DEFAULT 0으로 상품 등록 시 자동 초기화 (별도 선삽입 불필요) | D20 |
| 좋아요 데이터 유지 | 브랜드/상품 삭제 시 product_like 유지. 내 목록에서 ACTIVE 필터링 | D22 |
| restoreStock / cancel | 미구현 (현재 스코프 외. 주문 취소 API 없음) | — |
| 물리 FK | 미사용. 논리적 참조만. 애플리케이션 레벨에서 참조 무결성 보장 | — |
| 어드민 주문 조회 | validateOwner 없음. 전체 주문 조회 가능 | D18 |
| 인증 전략 통일 | 대고객: @MemberAuthenticated 어노테이션 선택 적용. 어드민: @AdminAuthenticated 어노테이션 클래스 레벨 일괄 적용 | D3 |
| VO 패턴 | `@JvmInline value class` + Entity primitive 저장. Service에서 `VO.of()` 생성. Hibernate 6.x AttributeConverter 미사용 | D23 |
| 스냅샷 범위 | productName, productPrice, brandName만 복사. quantity는 주문입력, amount는 파생값 | D25 |
| 주문 총액 비정규화 제거 | totalAmount 컬럼 미사용. `getTotalAmount() = orderItems.sumOf { it.amount }` 파생 계산 | D24 |
| 비밀번호 변경 시 캐시 eviction | MemberFacade에서 loginId 기반 auth-cache evict | D5 |
| 재고 동시성 제어 | 비관적 락(SELECT FOR UPDATE). Phase 1 기능 구현 시 포함 | D9 |
| 개발 순서 원칙 | Phase 1: 기능 정합성 → Phase 2: 동시성/멱등성/일관성/성능 | — |

---

## 공통 제약사항

| 제약 | 설명 |
|------|------|
| JWT 미사용 | 의도적 결정. 모든 인증은 `X-Loopers-LoginId` / `X-Loopers-LoginPw` 헤더 기반 |
| BCrypt 반복 호출 금지 | 매 요청 CPU-intensive 연산 반복 금지, 캐싱 레이어로 해결 |
| 사전 조회 중복체크 금지 | DB Unique Constraint + 예외 처리 방식 사용 |
| `synchronized` 금지 | Virtual Thread pinning 방지, `ReentrantLock` 사용 |
| `saveAndFlush()` 금지 | Hibernate write-behind 최적화 파괴 방지 |
| null-safety 필수 | Kotlin null-safety 활용 |
| TDD 워크플로우 | Red → Green → Refactor, 3A 원칙 (Arrange-Act-Assert) |
| 도메인 확장 패턴 | example 패키지 구조 참고, 4-layer 구조 유지 |

---

## 진행 현황 요약

### 기능 요구사항

| 도메인 | 전체 | 완료 | 설계완료 | 미착수 |
|--------|------|------|----------|--------|
| 회원 관리 | 3 | 3 | 0 | 0 |
| 브랜드 | 1 | 0 | 1 | 0 |
| 상품 | 1 | 0 | 1 | 0 |
| 좋아요 | 1 | 0 | 1 | 0 |
| 주문 | 1 | 0 | 1 | 0 |
| 쿠폰 | 1 | 0 | 0 | 1 |
| 결제 | 1 | 0 | 0 | 1 |
| 랭킹/추천 | 1 | 0 | 0 | 1 |
| **합계** | **10** | **3** | **4** | **3** |

### 성능 요구사항 (10K TPS)

| Phase | 전체 | 완료 | 보류 | 미착수 |
|-------|------|------|------|--------|
| Phase 1 | 5 | 5 | 0 | 0 |
| Phase 2 | 5 | 1 | 4 | 0 |
| Phase 3 | 3 | 0 | 0 | 3 |
| **합계** | **13** | **6** | **4** | **3** |
