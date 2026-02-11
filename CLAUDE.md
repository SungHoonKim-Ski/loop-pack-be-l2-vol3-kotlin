# CLAUDE.md

## 프로젝트 개요

**프로젝트명**: Loopers Kotlin Spring Template
**목적**: Spring Boot 기반의 멀티모듈 코틀린 커머스 템플릿 프로젝트
**성능 목표**: 10,000 TPS (피크 기준, 단계적 달성)

Loopers에서 제공하는 스프링 코틀린 템플릿 프로젝트입니다. 프로젝트 안정성 및 유지보수성을 위해 pre-commit 훅을 통한 ktlint 검사를 운용하고 있습니다.

## Getting Started

### Setup
커밋 이전에 `ktlint`를 통해 코드 스타일을 점검하여 코드 안정성을 확보합니다.
```bash
make init
```

### Environment
`local` 프로필로 동작할 수 있도록 필요 인프라를 Docker Compose로 제공합니다.
```bash
docker-compose -f ./docker/infra-compose.yml up
```

### Monitoring
`local` 환경에서 모니터링을 위해 Prometheus와 Grafana를 제공합니다.
애플리케이션 실행 후 **http://localhost:3000** 으로 접속하여 admin/admin 계정으로 로그인할 수 있습니다.
```bash
docker-compose -f ./docker/monitoring-compose.yml up
```

## 기술 스택 및 버전

| 기술 | 버전 |
|------|------|
| Kotlin | 2.0.20 |
| Java (JVM) | 21 |
| Spring Boot | 3.4.4 |
| Spring Cloud | 2024.0.1 |
| QueryDSL | jakarta |
| MySQL | 8.0 |
| Redis | 7.0 |
| Kafka | 3.5.1 |
| SpringDoc OpenAPI | 2.7.0 |

## 현재 인프라 상태

| 인프라 | 상태 | 비고 |
|--------|------|------|
| MySQL | 단일 DB | Read/Write 분리 미적용 |
| Redis | 모듈 존재, 미사용 | 캐싱 레이어 미적용 |
| Kafka | 구성 완료 | Streamer 앱에서 소비 |

## 인증 방식

JWT를 사용하지 않는다 (의도적 결정).

| 구분 | 헤더 | 적용 방식 |
|------|------|-----------|
| 대고객 | `X-Loopers-LoginId` + `X-Loopers-LoginPw` | `@Authenticated` 어노테이션 선택 적용 (비인증 API와 혼재) |
| 어드민 | `X-Loopers-Ldap: loopers.admin` | `/api-admin/**` 경로 패턴 일괄 적용 (전 엔드포인트 필수) |

## 모듈 구조

본 프로젝트는 멀티 모듈 프로젝트로 구성되어 있으며, 각 모듈의 위계 및 역할을 분명히 합니다.

### 모듈 규칙
- **apps**: 각 모듈은 실행 가능한 SpringBootApplication을 의미합니다.
- **modules**: 특정 구현이나 도메인에 의존적이지 않고, reusable한 configuration을 원칙으로 합니다.
- **supports**: logging, monitoring과 같이 부가적인 기능을 지원하는 add-on 모듈입니다.

### 디렉토리 구조
```
├── apps/                        # 실행 가능한 Spring Boot 애플리케이션
│   ├── commerce-api/            # REST API 애플리케이션
│   │   └── src/main/kotlin/com/loopers/
│   │       ├── interfaces/api/  # REST 컨트롤러, DTO, ApiSpec
│   │       │   ├── example/     # (템플릿) 예시 도메인 API
│   │       │   └── member/      # 회원 도메인 API
│   │       ├── application/     # Facade (유스케이스 조합), Info DTO
│   │       ├── domain/          # 도메인 모델, Service, Repository 인터페이스
│   │       ├── infrastructure/  # Repository 구현체, JPA Repository
│   │       ├── config/          # 앱 레벨 설정
│   │       └── support/         # 에러 핸들링 (ErrorType, CoreException)
│   ├── commerce-batch/          # 배치 처리 애플리케이션
│   └── commerce-streamer/       # Kafka 스트리밍 애플리케이션
│
├── modules/                     # 재사용 가능한 인프라 모듈
│   ├── jpa/                     # JPA/Hibernate & QueryDSL, DataSource
│   ├── redis/                   # Redis (Master-Replica) 설정
│   └── kafka/                   # Kafka Producer/Consumer 설정
│
├── supports/                    # 지원 모듈
│   ├── jackson/                 # Jackson 직렬화 설정
│   ├── logging/                 # 로깅 설정 (Logback, Slack)
│   └── monitoring/              # Prometheus & Micrometer 모니터링
│
└── docker/                      # 인프라 Docker Compose
    ├── infra-compose.yml        # MySQL, Redis, Kafka
    └── monitoring-compose.yml   # Grafana & Prometheus
```

### 새 도메인 추가 시 확장 패턴
example 패키지를 참고하여 다음 4개 패키지에 동일한 구조로 확장한다:
```
interfaces/api/{domain}/        → Controller, ApiSpec, Dto
interfaces/api/admin/{domain}/  → AdminController, AdminApiSpec, AdminDto
application/{domain}/           → Facade, AdminFacade, Info
domain/{domain}/                → Model(Entity), Service, Repository(interface), Command
infrastructure/{domain}/        → RepositoryImpl, JpaRepository
```
- **고객/어드민 Facade 분리**: Service는 공유, Facade만 분리. 전체 도메인에 일관 적용.
- **고객/어드민 DTO 분리**: Controller 레벨에서 노출 필드 분리. Facade는 동일 Info 반환.

## 주요 의존성

- **Web**: spring-boot-starter-web, springdoc-openapi
- **Data**: spring-boot-starter-data-jpa, querydsl-jpa, mysql-connector-j
- **Cache**: spring-boot-starter-data-redis
- **Messaging**: spring-kafka
- **Batch**: spring-boot-starter-batch
- **Security**: spring-security-crypto
- **Monitoring**: micrometer-registry-prometheus, micrometer-tracing-bridge-brave
- **Testing**: JUnit 5, MockK, Mockito, TestContainers

## 빌드 및 실행 명령어

```bash
# 전체 빌드
./gradlew build

# API 서버 실행
./gradlew :apps:commerce-api:bootRun

# 배치 서버 실행
./gradlew :apps:commerce-batch:bootRun

# 스트리머 서버 실행
./gradlew :apps:commerce-streamer:bootRun
```

## 테스트

- **JUnit 5** + Spring Boot Test
- **MockK** (Kotlin 모킹)
- **TestContainers** (MySQL, Redis, Kafka)
- **JaCoCo** 코드 커버리지

## 패키지 구조 (Layered Architecture)

```
com.loopers
├── interfaces.api           # REST 컨트롤러, DTO, ApiSpec
├── application              # Facade (유스케이스 조합), Info DTO
├── domain                   # 도메인 모델(Entity), Service, Repository 인터페이스, Command
├── infrastructure           # Repository 구현체, JPA Repository
├── config                   # 앱 레벨 설정
└── support                  # 에러 핸들링, 유틸리티
```

## 10K TPS 로드맵

현재 → 목표까지 단계적으로 적용한다. 각 단계는 이전 단계에 의존하지 않으며 독립적으로 적용 가능하다.

### Phase 1: 즉시 적용 (코드 변경 최소) ✅
| 항목 | 현재 | 목표 | 상태 |
|------|------|------|------|
| Virtual Threads | 미적용 | `spring.threads.virtual.enabled=true` | DONE |
| Tomcat 튜닝 | max-threads=200, max-connections=8192 | max-connections=10000, accept-count=200 | DONE |
| DB 커넥션 풀 | max=40 | max=50 (단일 DB, Phase 2에서 R/W 분리 시 확장) | DONE |
| 회원가입 중복체크 | findByLoginId + findByEmail (2회 조회) | Unique Constraint + Facade에서 DataIntegrityViolationException 처리 | DONE |
| 인증 헤더 통일 | X-LOGIN-ID / X-PASSWORD | X-Loopers-LoginId / X-Loopers-LoginPw + Interceptor + ArgumentResolver | DONE |

### Phase 2: 캐싱 및 인프라 확장
| 항목 | 현재 | 목표 | 상태 |
|------|------|------|------|
| 인증 BCrypt 캐싱 | 매 요청 BCrypt 호출 | Caffeine 로컬 캐시 (auth-cache, TTL 5분, max 10K) + SHA256 비교 | DONE |
| 비밀번호 변경 시 캐시 eviction | 미적용 | MemberFacade에서 loginId 기반 evict | DONE |
| DB Read/Write 분리 | 단일 DB | AbstractRoutingDataSource + Replica | TODO (보류) |
| Redis 캐싱 도입 | 미사용 | Spring Cache Abstraction으로 향후 전환 가능 | TODO (보류) |
| Lettuce 커넥션 풀링 | 단일 커넥션 멀티플렉싱 | LettucePoolingClientConfiguration | TODO (보류) |

### Phase 3: 안정성 강화
| 항목 | 현재 | 목표 | 상태 |
|------|------|------|------|
| Circuit Breaker | 없음 | Resilience4j | TODO |
| Rate Limiting | 없음 | API 레벨 제한 | TODO |
| Graceful Degradation | 없음 | 폴백 패턴 | TODO |

## 아키텍처 원칙 (확장 시 준수)

- `@Transactional(readOnly = true)` 를 읽기 메서드에 반드시 명시 → 향후 Read Replica 라우팅 자동 대응
- `synchronized` 대신 `ReentrantLock` → Virtual Thread pinning 방지
- 사전 조회로 중복 체크하지 않음 → DB Unique Constraint + 예외 처리
- 매 요청 CPU-intensive 연산 반복 금지 → 캐싱 레이어 도입 시 자연스럽게 해소
- 도메인 간 결합 최소화 → 향후 서비스 분리 대비 (ID 참조, 물리 FK 미사용)
- 도메인 모델이 자기 불변식 보호 → `deductStock()`, `validateOwner()`, `delete()` 등 비즈니스 규칙은 모델 내부
- cross-domain 접근은 Facade 레벨에서만 조합 → Service 간 직접 참조 금지
- Soft Delete: `status=DELETED` + `deleted_at` 병행 → `delete()` 메서드에서 동시 설정
- 개발 순서: Phase 1 기능 정합성 → Phase 2 동시성/멱등성/일관성

### Value Object (VO) 컨벤션

| 규칙 | 설명 |
|------|------|
| 구현 방식 | `@JvmInline value class` (Hibernate 호환을 위해 `AttributeConverter` 미사용) |
| 패키지 | 도메인별: `domain/{domain}/vo/`, 공통: `domain/common/vo/` |
| 생성 경계 | Service 레이어에서 `VO.of()` 팩토리로 생성 |
| 생성자 | 검증 없음 (DB 복원용). `of()`에서만 검증 |
| Entity 필드 | 생성자는 VO 타입 수신, 내부 필드는 primitive 저장 (`var loginId: String = loginId.value`) |
| Repository 쿼리 | 파라미터는 primitive 유지 (findByLoginId(String)) |
| VO 대상 | 도메인 검증 규칙이 있는 필드 (regex, 범위, 형식) |
| VO 비대상 | id, LocalDate, ZonedDateTime, Enum, Boolean |
| Password | `object RawPassword`로 `domain/{domain}/`에 배치 (저장 안 되므로 value class 아님, VO 아님) |
| Facade/Controller | primitive 유지 (VO 노출하지 않음) |

## 개발 규칙

### 진행 Workflow - 증강 코딩
- **대원칙**: 방향성 및 주요 의사 결정은 개발자에게 제안만 할 수 있으며, 최종 승인된 사항을 기반으로 작업을 수행
- **중간 결과 보고**: AI가 반복적인 동작을 하거나, 요청하지 않은 기능을 구현, 테스트 삭제를 임의로 진행할 경우 개발자가 개입
- **설계 주도권 유지**: AI가 임의판단을 하지 않고, 방향성에 대한 제안 등을 진행할 수 있으나 개발자의 승인을 받은 후 수행

### 개발 Workflow - TDD (Red > Green > Refactor)
모든 테스트는 3A 원칙으로 작성할 것 (Arrange - Act - Assert)

#### 1. Red Phase: 실패하는 테스트 먼저 작성
- 요구사항을 만족하는 기능 테스트 케이스 작성
- 테스트 예시 코드 포함

#### 2. Green Phase: 테스트를 통과하는 코드 작성
- Red Phase의 테스트가 모두 통과할 수 있는 코드 작성
- 오버엔지니어링 금지

#### 3. Refactor Phase: 불필요한 코드 제거 및 품질 개선
- 불필요한 private 함수 지양, 객체지향적 코드 작성
- unused import 제거
- 성능 최적화
- 모든 테스트 케이스가 통과해야 함

### 주의사항

#### 1. Never Do
- 실제 동작하지 않는 코드, 불필요한 Mock 데이터를 이용한 구현을 하지 말 것
- null-safety 하지 않게 코드 작성하지 말 것 (Java의 경우, Optional을 활용할 것)
- println 코드 남기지 말 것
- 매 요청마다 BCrypt 등 CPU-intensive 연산을 반복하지 말 것
- 사전 조회로 중복 체크하지 말 것 (DB Unique Constraint 활용)

#### 2. Recommendation
- 실제 API를 호출해 확인하는 E2E 테스트 코드 작성
- 재사용 가능한 객체 설계
- 성능 최적화에 대한 대안 및 제안
- 개발 완료된 API의 경우, `.http/**.http`에 분류해 작성

#### 3. Priority
1. 실제 동작하는 해결책만 고려
2. null-safety, thread-safety 고려
3. 테스트 가능한 구조로 설계
4. 기존 코드 패턴 분석 후 일관성 유지
5. 10K TPS 성능 목표에 부합하는 구현

## agent_todo

- 요구사항이 모호하면 진행 전에 확인 질문을 한다.
- 변경 범위를 최소화하고 모듈 구조 및 아키텍처에 맞게 작업한다.
- 추천 운영 방식(스펙/구현/테스트 분리, 독립 모듈 병렬·공유 자원 순차, 부분 테스트 → 전체 테스트)을 기본으로 따른다.
- 관련 테스트를 선택적으로 실행하고, 미실행 시 이유를 명시한다.
- `user_todo`가 지켜지지 않으면 준수를 요청한다. 단, 사용자가 `ignore_user_todo` 명령을 명시하면 해당 요청에 한해 무시한다.
- 새 도메인 추가 시 example 패키지 구조를 참고하여 4-layer 구조를 유지한다.
- 성능에 영향을 주는 변경은 아키텍처 원칙 및 로드맵과의 일관성을 확인한다.
- 개발자가 반복적으로 언급하는 대원칙이나 요구사항이 있다면, 이를 CLAUDE.md에 반영하여 이후 세션에서도 일관되게 적용되도록 한다.
- 새로운 요구사항이 확정되면 `REQUIREMENTS.md`에 추가한다 (REQ 번호, 배경, 수용 기준, 제약사항, 상태 포함).
- 기술 판단(선택지 비교, 트레이드오프)이 발생하면 `DECISIONS.md`에 기록한다 (배경, 선택지, 판단, 근거, 트레이드오프 포함).
- 요구사항 상태가 변경되면(TODO → DONE, 보류 등) `REQUIREMENTS.md`와 `CLAUDE.md` 로드맵을 함께 업데이트한다.

## user_todo

- 작업 목표, 적용 범위(모듈/패키지), 수용 기준을 제공한다.
- 변경 제약(금지 사항, 성능/보안 요구, 마감)을 알려준다.
- 필요한 입력 데이터/샘플이 있으면 제공한다.
- `ignore_user_todo` 명령을 사용하면 해당 요청에 한해 위 항목을 생략할 수 있다.
