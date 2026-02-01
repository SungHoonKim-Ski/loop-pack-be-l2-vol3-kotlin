# CLAUDE.md

## 프로젝트 개요

**프로젝트명**: Loopers Kotlin Spring Template
**목적**: Spring Boot 기반의 멀티모듈 코틀린 템플릿 프로젝트

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

## 모듈 구조

본 프로젝트는 멀티 모듈 프로젝트로 구성되어 있으며, 각 모듈의 위계 및 역할을 분명히 합니다.

### 모듈 규칙
- **apps**: 각 모듈은 실행 가능한 SpringBootApplication을 의미합니다.
- **modules**: 특정 구현이나 도메인에 의존적이지 않고, reusable한 configuration을 원칙으로 합니다.
- **supports**: logging, monitoring과 같이 부가적인 기능을 지원하는 add-on 모듈입니다.

### 디렉토리 구조
```
├── apps/                    # 실행 가능한 Spring Boot 애플리케이션
│   ├── commerce-api         # REST API 애플리케이션
│   ├── commerce-batch       # 배치 처리 애플리케이션
│   └── commerce-streamer    # Kafka 스트리밍 애플리케이션
│
├── modules/                 # 재사용 가능한 모듈
│   ├── jpa                  # JPA/Hibernate & QueryDSL 설정
│   ├── redis                # Redis 설정 (Master-Replica)
│   └── kafka                # Kafka 설정
│
├── supports/                # 지원 모듈
│   ├── jackson              # Jackson 설정
│   ├── logging              # 로깅 설정 (Logback, Slack)
│   └── monitoring           # Prometheus & Micrometer 모니터링
│
└── docker/                  # 인프라 Docker Compose
    ├── infra-compose.yml    # MySQL, Redis, Kafka
    └── monitoring-compose.yml # Grafana & Prometheus
```

## 주요 의존성

- **Web**: spring-boot-starter-web, springdoc-openapi
- **Data**: spring-boot-starter-data-jpa, querydsl-jpa, mysql-connector-j
- **Cache**: spring-boot-starter-data-redis
- **Messaging**: spring-kafka
- **Batch**: spring-boot-starter-batch
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
├── interfaces.api           # REST 컨트롤러
├── application              # 비즈니스 로직/파사드
├── domain                   # 도메인 모델
├── infrastructure           # 리포지토리 구현
└── support                  # 에러 핸들링, 유틸리티
```

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

## agent_todo

- 요구사항이 모호하면 진행 전에 확인 질문을 한다.
- 변경 범위를 최소화하고 모듈 구조 및 아키텍처에 맞게 작업한다.
- 추천 운영 방식(스펙/구현/테스트 분리, 독립 모듈 병렬·공유 자원 순차, 부분 테스트 → 전체 테스트)을 기본으로 따른다.
- 관련 테스트를 선택적으로 실행하고, 미실행 시 이유를 명시한다.
- `user_todo`가 지켜지지 않으면 준수를 요청한다. 단, 사용자가 `ignore_user_todo` 명령을 명시하면 해당 요청에 한해 무시한다.

## user_todo

- 작업 목표, 적용 범위(모듈/패키지), 수용 기준을 제공한다.
- 변경 제약(금지 사항, 성능/보안 요구, 마감)을 알려준다.
- 필요한 입력 데이터/샘플이 있으면 제공한다.
- `ignore_user_todo` 명령을 사용하면 해당 요청에 한해 위 항목을 생략할 수 있다.
