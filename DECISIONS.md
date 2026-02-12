# Architecture Decision Records

10K TPS 목표 달성을 위한 Phase 1-2 구현 과정에서 내린 주요 기술 판단과 그 근거를 기록합니다.

---

## 1. Virtual Threads 활성화 (Kotlin + Spring MVC 환경)

### 배경
Kotlin 환경에서 비동기 처리는 코루틴이 관용적이다. JDK 21 Virtual Threads를 Kotlin 프로젝트에 적용하는 것이 의미 있는지에 대한 판단이 필요했다.

### 선택지
| 선택지 | 설명 |
|--------|------|
| **A. Virtual Threads 활성화** | `spring.threads.virtual.enabled=true` 설정만으로 적용 |
| B. Kotlin Coroutines + WebFlux 전환 | 비동기 스택으로 전면 전환 |
| C. 현행 유지 (Platform Threads) | Tomcat thread pool 200개 기반 |

### 판단: A. Virtual Threads 활성화

### 근거
- **현재 스택이 blocking**: Spring MVC + JPA + JDBC는 모두 blocking I/O 기반이다. 코루틴을 도입하려면 WebFlux + R2DBC로 전면 전환해야 하며, 이는 기존 코드 전체를 재작성하는 수준의 변경이다.
- **코드 변경 최소**: 설정 한 줄로 Tomcat 워커 스레드가 Virtual Thread로 전환된다. 기존 동기 코드를 그대로 유지하면서 스레드 풀 고갈 문제를 해결한다.
- **JPA blocking I/O에 효과적**: DB 쿼리 대기 시간 동안 Virtual Thread가 yield되므로, Platform Thread 200개 제한 없이 수만 개의 동시 요청을 처리할 수 있다.
- **Spring Boot 3.4의 공식 지원**: Spring Framework 6.1부터 Virtual Threads를 공식 지원하며, Tomcat, JPA, JDBC 모두 호환이 검증되었다.

### 트레이드오프
- `synchronized` 블록에서 Virtual Thread pinning이 발생할 수 있다 → 아키텍처 원칙에 `ReentrantLock` 사용을 명시하여 대응
- Kotlin 코루틴 생태계와의 혼용 시 혼란 가능 → 현재 프로젝트는 코루틴을 사용하지 않으므로 문제 없음
- 디버깅 시 스택 트레이스가 기존과 다를 수 있음 → JDK 21+ 도구 지원으로 점진적 개선 중

---

## 2. 회원가입 중복체크: 사전 조회 → Unique Constraint

### 배경
기존 회원가입은 `findByLoginId` + `findByEmail`로 2회 SELECT 후 중복 여부를 판단했다. 고트래픽 환경에서 race condition과 불필요한 DB 조회가 문제될 수 있다.

### 선택지
| 선택지 | 설명 |
|--------|------|
| **A. DB Unique Constraint + 예외 처리** | INSERT 시 DB가 중복을 거부, 예외로 처리 |
| B. 사전 조회 유지 + 비관적 락 | SELECT FOR UPDATE로 race condition 방지 |
| C. 사전 조회 유지 + 분산 락 | Redis 등으로 분산 락 적용 |

### 판단: A. DB Unique Constraint + 예외 처리

### 근거
- **원자성 보장**: DB 레벨에서 유니크 제약이 걸리므로 어떤 동시성 시나리오에서도 중복이 발생하지 않는다. 사전 조회 방식은 check-then-act 패턴으로, 조회와 삽입 사이에 다른 트랜잭션이 끼어들 수 있다.
- **쿼리 수 감소**: 정상 케이스에서 2회 SELECT가 제거되어 INSERT 1회로 완료된다. 중복은 예외 상황이므로 대부분의 요청에서 DB 부하가 절반으로 줄어든다.
- **코드 단순화**: 중복 체크 로직이 DB 제약으로 위임되어 서비스 레이어가 단순해진다.

### 트레이드오프
- 중복 시 `DataIntegrityViolationException`이 발생하는데, 이를 적절한 HTTP 응답(409 CONFLICT)으로 변환해야 한다 → Facade 레이어에서 catch하여 `CoreException`으로 변환
- 어떤 필드가 중복인지(loginId vs email) 특정하기 어렵다 → 현재는 "이미 존재하는 회원 정보입니다"로 통합 처리. 향후 필요 시 예외 메시지 파싱으로 분기 가능
- `@Transactional` 경계 밖에서 예외를 잡아야 한다 → Facade가 Service 위에 있으므로 자연스럽게 해결

### `saveAndFlush()` 사용하지 않은 이유
`saveAndFlush()`로 즉시 DB에 반영하면 Service 내부에서 예외를 잡을 수 있지만, Hibernate의 write-behind 최적화를 깨뜨린다. 여러 엔티티를 배치로 처리하거나 dirty checking으로 최적화하는 JPA의 핵심 전략을 포기하는 것이므로, 트랜잭션 커밋 시점에 자연스럽게 flush되도록 두고 Facade에서 예외를 처리하는 방식을 선택했다.

---

## 3. 인증 처리: Interceptor + ArgumentResolver 패턴

### 배경
기존에는 각 Controller 메서드에서 `@RequestHeader`로 인증 헤더를 직접 받아 처리했다. 인증 로직이 Controller마다 중복되었고, 새 API 추가 시마다 동일한 인증 코드를 반복해야 했다.

### 선택지
| 선택지 | 설명 | 실행 시점 |
|--------|------|-----------|
| A. Servlet Filter | `javax.servlet.Filter` 구현 | DispatcherServlet 이전 |
| **B. Interceptor + ArgumentResolver** | `HandlerInterceptor`에서 인증, `ArgumentResolver`에서 주입 | Handler 매핑 이후, Controller 이전 |
| C. AOP (@Around) | `@MemberAuthenticated` 어노테이션 기반 AOP | 메서드 실행 시점 |
| D. Spring Security Filter Chain | Spring Security의 인증 필터 체인 | DispatcherServlet 이전 |

### 판단: B. Interceptor + ArgumentResolver

### 근거
- **Handler 메서드 수준 제어**: `@MemberAuthenticated` 어노테이션이 붙은 메서드만 선택적으로 인증을 적용할 수 있다. Filter는 URL 패턴 기반이라 세밀한 제어가 어렵다.
- **Spring MVC와의 자연스러운 통합**: `HandlerMethod`를 통해 어노테이션 정보에 접근할 수 있고, `ArgumentResolver`로 Controller 파라미터에 인증 객체를 직접 주입할 수 있다. Controller 코드가 `AuthenticatedMember`만 받으면 되므로 매우 깔끔하다.
- **AOP 대비 장점**: AOP는 메서드 실행 시점에 동작하므로, Controller의 파라미터 바인딩보다 늦다. 인증 실패 시 불필요한 요청 파싱이 먼저 수행된다. Interceptor는 Controller 진입 전에 차단하므로 더 효율적이다.
- **관심사 분리**: 인증(Interceptor) → 파라미터 주입(ArgumentResolver) → 비즈니스 로직(Controller)로 책임이 명확히 분리된다.

### 트레이드오프
- Filter 대비 Spring MVC에 결합됨 → 현재 프로젝트는 100% Spring MVC이므로 문제 없음
- Spring Security 미사용 → JWT를 사용하지 않는 의도적 결정이므로, Security Filter Chain의 복잡성을 도입할 이유가 없음
- Interceptor에서 예외 발생 시 `@ControllerAdvice`의 예외 처리기가 동작함 → 기존 `CoreException` 기반 에러 핸들링과 호환

### 보완: 대고객/어드민 인증 적용 방식 통일
- **대고객** (`MemberAuthenticationInterceptor`): `@MemberAuthenticated` 어노테이션 기반 선택 적용. 비인증 API(상품 조회)와 인증 API(좋아요, 주문)가 혼재하므로 메서드 단위 제어 필요. `@Target(FUNCTION)`으로 제한하여 클래스 레벨 적용을 차단 — 비인증 엔드포인트가 실수로 인증 처리되는 것을 방지.
- **어드민** (`AdminAuthenticationInterceptor`): `@AdminAuthenticated` 어노테이션 기반 적용. `@Target(CLASS, FUNCTION)`으로 클래스 레벨 어노테이션을 허용하여 컨트롤러 전체에 일괄 적용, 누락 방지. 전 엔드포인트가 LDAP 인증 필수이므로 클래스 단위 적용이 적합.

---

## 4. 캐시 백엔드: Caffeine 로컬 캐시 vs Redis

### 배경
매 인증 요청마다 BCrypt 비교(`passwordEncoder.matches()`)가 수행되며, 이는 약 100ms가 소요된다. 10K TPS 달성을 위해 인증 결과를 캐싱해야 한다.

### 선택지
| 선택지 | 설명 |
|--------|------|
| **A. Caffeine 로컬 캐시** | JVM 내 인메모리 캐시, 네트워크 없음 |
| B. Redis 분산 캐시 | 외부 Redis 서버에 캐시 |
| C. Caffeine (L1) + Redis (L2) | 2-tier 캐시 |

### 판단: A. Caffeine 로컬 캐시

### 근거
- **현재 인프라 상태**: Redis 모듈은 존재하지만 실제로 사용되지 않는 상태다. Redis를 캐시 용도로 도입하면 운영 복잡성(모니터링, 장애 대응, 네트워크 지연)이 추가된다.
- **네트워크 비용 제거**: 로컬 캐시는 네트워크 라운드트립이 없다. Redis는 아무리 빨라도 0.1~1ms의 네트워크 지연이 있지만, Caffeine은 나노초 단위다.
- **단일 인스턴스 환경에 적합**: 현재 단일 DB, 단일 앱 서버 환경이다. 분산 캐시의 장점(멀티 인스턴스 간 캐시 공유)이 필요한 시점이 아니다.
- **Spring Cache Abstraction 기반**: `CacheManager` 인터페이스를 통해 구현했으므로, 향후 스케일아웃 시 Redis로 전환할 때 `CacheConfig`만 교체하면 된다.

### 트레이드오프
- 멀티 인스턴스 환경에서 캐시 불일치 발생 가능 → 현재 단일 인스턴스이므로 문제 없음. 스케일아웃 시 Redis 전환 필요
- JVM 힙 메모리 사용 → `maximumSize(10_000)`으로 제한하여 메모리 사용량 예측 가능 (약 수 MB 수준)
- 앱 재시작 시 캐시 소실 → cold start 시 BCrypt 호출이 발생하나, warm-up 후 정상 성능 복귀

---

## 5. 캐시 키 설계: loginId + SHA256 다이제스트

### 배경
인증 캐시의 키를 어떻게 설계할지 결정해야 했다. 캐시 히트 시 BCrypt를 건너뛰어야 하므로, 비밀번호 일치 여부도 캐시에서 판단할 수 있어야 한다.

### 선택지
| 선택지 | 설명 |
|--------|------|
| A. `loginId:sha256(password)`를 키로 사용 | 키 자체에 비밀번호 해시 포함 |
| **B. `loginId`를 키, SHA256 다이제스트를 값에 포함** | 키는 단순, 값에서 비밀번호 검증 |
| C. `loginId`를 키, BCrypt 해시를 값에 포함 | 캐시 히트 후 BCrypt로 재검증 |

### 판단: B. loginId를 키, SHA256 다이제스트를 값에 포함

### 근거
- **캐시 eviction 용이**: 비밀번호 변경 시 `loginId`만으로 캐시를 evict할 수 있다. 선택지 A는 키에 비밀번호 해시가 포함되어 있어 eviction 시 비밀번호를 알아야 한다.
- **SHA256 비교는 무시할 수 있는 비용**: SHA256 연산은 ~μs 수준으로, BCrypt의 ~100ms 대비 10만 배 빠르다. 캐시 히트 시 SHA256 비교만으로 비밀번호 일치를 확인할 수 있다.
- **선택지 C의 문제**: BCrypt 해시를 값에 저장하고 `passwordEncoder.matches()`로 재검증하면, 캐싱의 의미가 없다. BCrypt 호출 자체가 병목이기 때문이다.
- **비밀번호 불일치 처리**: 캐시에 loginId가 있지만 SHA256이 다르면 (비밀번호 틀림), `memberService.authenticate()`를 호출하여 정확한 BCrypt 검증을 수행한다. 이로써 오래된 캐시와 잘못된 비밀번호 모두 올바르게 처리된다.

### 트레이드오프
- SHA256은 BCrypt와 달리 보안용 해시가 아니다 → 캐시는 JVM 메모리 내부에만 존재하며 외부 노출되지 않으므로, 브루트포스 공격 대상이 아니다. DB에 저장되는 비밀번호는 여전히 BCrypt다.
- loginId당 캐시 엔트리가 1개 → 동일 loginId에 대해 최신 인증 결과만 캐싱되며, 이전 비밀번호 캐시는 자연스럽게 덮어씌워진다.

---

## 6. DB Read/Write 분리 및 Redis 도입 보류

### 배경
Phase 2 로드맵에는 MySQL Replica를 통한 R/W 분리와 Redis 캐싱 도입이 포함되어 있었다.

### 판단: 보류

### 근거
- **현재 병목이 아님**: Phase 1의 Virtual Threads + Phase 2의 Caffeine 캐싱으로 가장 큰 병목(BCrypt, 스레드 풀 고갈)이 해소되었다. DB가 병목이 되기 전에 인프라를 확장하는 것은 과잉 설계다.
- **운영 복잡성 증가**: MySQL Replica를 도입하면 `AbstractRoutingDataSource`, 복제 지연(replication lag) 처리, read-after-write 일관성 문제를 다루어야 한다. Redis 도입은 별도 인프라 운영 부담이 추가된다.
- **점진적 확장 가능**: `@Transactional(readOnly = true)`를 이미 읽기 메서드에 명시해두었으므로, Replica 도입 시 라우팅이 자동으로 적용된다. Caffeine → Redis 전환도 `CacheConfig`만 교체하면 된다.
- **측정 기반 결정**: 실제 부하 테스트 후 병목 지점이 DB인지 확인한 뒤 도입하는 것이 합리적이다.

### 트레이드오프
- 스케일아웃 시점에 추가 작업 필요 → 아키텍처 원칙으로 미래 확장을 위한 코드 컨벤션은 이미 적용됨
- 단일 DB 장애 시 전체 서비스 중단 → Phase 3 안정성 강화(Circuit Breaker, Graceful Degradation)에서 대응 예정

---

## 7. JWT 미사용 (헤더 기반 인증)

### 배경
프로젝트 특성상 인증 방식을 결정해야 했다. 매 요청마다 `X-Loopers-LoginId` / `X-Loopers-LoginPw` 헤더로 인증하고, Interceptor에서 DB 조회 + BCrypt 검증을 수행한다 (Caffeine 캐시로 최적화).

### 선택지
| 선택지 | 설명 |
|--------|------|
| A. JWT 토큰 기반 | 발급/갱신/블랙리스트 관리 필요. Stateless |
| **B. 헤더 기반 (매 요청 인증)** | 단순. Caffeine 캐시로 BCrypt 비용 완화 |
| C. 세션 기반 | 서버 상태 유지. 스케일아웃 시 세션 공유 필요 |

### 판단: B. 헤더 기반 매 요청 인증

### 근거
- 프로젝트의 학습 및 템플릿 목적에 부합하는 단순한 인증 방식
- 토큰 관리(발급, 갱신, 블랙리스트)의 복잡성을 의도적으로 배제
- Caffeine 캐시를 통해 매 요청 BCrypt 비용 문제를 해결하여 성능 트레이드오프를 완화

### 트레이드오프
- 매 요청마다 비밀번호가 네트워크를 통해 전송됨 → HTTPS 필수, 내부 네트워크 기준으로 수용 가능
- Stateless 장점 포기 → 현재 단일 인스턴스 환경이므로 세션 공유 문제 없음

---

## 8. 고객/어드민 Facade 분리

### 배경
고객 API와 어드민 API가 같은 Facade를 공유하고 있었다. 향후 어드민에 참조 테이블 조인, 통계 조회 등 고객과 다른 유스케이스가 추가될 경우 공유 Facade를 분리하는 리팩토링이 발생한다.

### 선택지
| 선택지 | 설명 |
|--------|------|
| A. Facade 공유 | YAGNI 원칙. 현재 비용 없음, 확장 시 분리 리팩토링 필요 |
| B. Facade 분리 | 얇은 클래스 추가. 확장 시 독립 진화 가능 |
| **C. Service 공유, Facade만 분리** | 비즈니스 로직 중복 없이 유스케이스만 분리 |

### 판단: C. Service 공유, Facade만 분리. 전체 도메인에 일괄 적용.

### 근거
- Service는 공유하여 비즈니스 로직 중복 방지. Facade만 분리하여 유스케이스 독립성 확보.
- 전체 도메인에 일관 적용하여 패턴 불일치 방지.
- 현재는 고객/어드민 Facade 로직이 유사하지만, 확장 시 AdminFacade만 수정하면 됨.

### 트레이드오프
- 현재 로직이 동일한 Facade 쌍이 다수 생김 → 일관성 대비 코드 증가. 각 Facade가 얇으므로 유지보수 부담 최소.

---

## 9. 재고 검증 위치: 도메인 모델 내부 (C+D 조합)

### 배경
주문 시 재고 검증을 어느 레이어에서 수행할지 결정이 필요했다.

### 선택지
| 선택지 | 설명 |
|--------|------|
| A. Facade에서 직접 검증 | Facade에 비즈니스 로직 유입 |
| B. Service에 Validator 분리 | 검증 로직 재사용 가능, TOCTOU 가능성 |
| **C+D. Domain Model + Service 통합** | 모델이 불변식 보호, Service가 위임 |

### 판단: C+D. `ProductModel.deductStock(qty)`이 불변식 보호, `ProductService.deductStock()`이 위임.

### 근거
- 도메인 모델이 `stockQuantity >= qty` 규칙을 자체 보호 (가장 OOP적).
- Facade는 비즈니스 판단 없이 Service 호출만 수행. 레이어 책임 명확.
- 향후 동시성 처리 시 Service 레벨에서 비관적 락 추가로 자연스럽게 확장.

### 트레이드오프
- 개별 상품 단위 검증 (전체 주문의 사전 일괄 검증 불가) → 트랜잭션 내에서 순차 차감하므로 실패 시 전체 롤백으로 보장.

---

## 10. 주문 본인 검증 위치: 도메인 모델 내부

### 배경
주문 상세 조회 시 "본인 주문만 조회 가능" 규칙의 검증 위치를 결정해야 했다.

### 선택지
| 선택지 | 설명 |
|--------|------|
| A. Service에서 검증 | Service가 인증 정보에 의존 |
| B. Facade에서 검증 | Facade에 비즈니스 로직 유입 |
| **C. Domain Model에서 검증** | 도메인 규칙은 도메인이 보호 |

### 판단: C. `OrderModel.validateOwner(memberId)`.

### 근거
- "본인만 조회 가능"은 Order 도메인의 비즈니스 규칙. Decision 9와 동일한 사고방식 적용.
- Service는 모델에 위임만 하고, Facade는 조합만 수행.
- 어드민 조회(`getOrderById`)는 `validateOwner`를 호출하지 않아 고객/어드민 분기가 Service 메서드 분리로 자연스럽게 해결됨.

### 트레이드오프
- 도메인 모델에 인증 관련 검증이 포함됨 → "본인 확인"은 접근 제어가 아닌 도메인 규칙이므로 적합.

---

## 11. Soft Delete 방식: status + deleted_at 병행

### 배경
브랜드/상품 삭제 시 `status=DELETED`와 `deleted_at` 중 어떤 방식을 사용할지, 또는 병행할지 결정이 필요했다.

### 선택지
| 선택지 | 설명 |
|--------|------|
| A. deleted_at만 | BaseEntity 패턴, 삭제 시각 기록. 상태와 삭제가 별도 관심사 |
| B. status만 | 상태 일원화. 삭제 시각 기록 불가 |
| **C. 둘 다** | 상태와 시각 모두 보유. 불일치 위험 존재 |

### 판단: C. status + deleted_at 병행. `delete()` 메서드에서 동시 설정.

### 근거
- `status`: 비즈니스 상태 표현. 대고객 쿼리에서 `WHERE status = 'ACTIVE'` 필터링.
- `deleted_at`: 감사(audit) 목적. BaseEntity 패턴 유지.
- 도메인 모델의 `delete()` 메서드에서 동시 설정하여 불일치 방지.
- `@SQLRestriction` 자동 필터링은 어드민에서 삭제 데이터 조회가 필요할 수 있으므로 부적합.

### 트레이드오프
- 중복 관리 부담 → `delete()` 메서드에서 동시 설정하므로 실질적 위험 낮음.

---

## 12. 좋아요 수 집계: product 비정규화 + 배치

### 배경
상품 조회 시 좋아요 수를 어떻게 제공할지 결정이 필요했다. 10K TPS 환경에서 런타임 COUNT는 부하 문제.

### 선택지
| 선택지 | 설명 |
|--------|------|
| A. 런타임 COUNT | 매 조회마다 JOIN/서브쿼리. 10K TPS에서 부하 |
| **B. product에 like_count 비정규화** | 배치 갱신으로 write contention 해소. eventual consistency |
| C. 별도 집계 테이블 + 배치 | 읽기/쓰기 분리되나 JOIN 필요. 테이블 관리 부담 |
| D. Redis 카운터 | 가장 빠르지만 인프라 추가 필요 |

### 판단: B. `product.like_count` 컬럼 (DEFAULT 0) + `commerce-batch` 배치 갱신.

### 근거
- 10K TPS에서 런타임 COUNT는 DB 부하 집중.
- 배치 갱신이므로 인기 상품 hot row 문제 없음 (실시간 write가 아닌 배치 UPDATE).
- 별도 집계 테이블 대비 JOIN 불필요 → 단순 컬럼 조회로 성능 우위.
- DEFAULT 0으로 상품 등록 시 별도 초기화 불필요 (선삽입 제거).
- `commerce-batch` 모듈이 이미 존재하여 추가 인프라 불필요.
- 향후 랭킹 기능 시 별도 `product_like_daily_stats` 테이블 추가로 확장 가능.

### 트레이드오프
- 최대 배치 주기만큼의 수치 지연 → 수용 가능 (좋아요 수의 실시간 정확성은 비크리티컬)
- 배치 실패 시 수치 정체 → 배치 모니터링으로 대응

---

## 13. 주문 번호: UUID

### 배경
주문 식별자를 내부 auto-increment ID로 노출할지, 별도 주문 번호를 생성할지 결정이 필요했다.

### 선택지
| 선택지 | 설명 |
|--------|------|
| **A. UUID** | 추측 불가, 서비스 분리 시 ID 독립 |
| B. 날짜+시퀀스 | 가독성 좋으나 생성 로직 복잡 |
| C. 미포함 (ID만 사용) | 단순하지만 보안/확장성 이슈 |

### 판단: A. UUID.

### 근거
- 내부 auto-increment ID 노출 방지 (일일 주문량 추측 차단).
- 서비스 분리 시 ID 체계 독립성 확보.
- UUID 생성 비용 거의 없음. UNIQUE 인덱스만 추가.

### 트레이드오프
- UUID는 인덱스 성능에 불리 (랜덤 값으로 인한 B-Tree 분산) → 현재 규모에서 무시 가능. 대규모 시 UUIDv7(시간 정렬) 전환 가능.

---

## 14. INACTIVE/SUSPENDED/SOLD_OUT 상태 미포함

### 배경
BrandStatus, ProductStatus에 INACTIVE, SUSPENDED, SOLD_OUT을 포함할지 결정이 필요했다.

### 판단: 모두 미포함. ProductStatus = ACTIVE / DELETED (BrandStatus와 일관).

### 근거
- INACTIVE/SUSPENDED: 현재 요구사항에 비활성화/판매중단 시나리오가 없음 (YAGNI).
- SOLD_OUT: `stockQuantity == 0`이 이미 품절을 표현. 별도 상태는 중복 정보. 상태 전환 로직(deductStock→SOLD_OUT, restock→ACTIVE) 불필요.
- 고객에게 품절 표시: `soldOut: Boolean` 파생 필드로 전달 (stockQuantity 미노출 유지).
- BrandStatus와 ProductStatus가 동일한 패턴(ACTIVE/DELETED)으로 일관.
- 필요 시 Enum에 값 추가만으로 확장 가능.

---

## 15. 고객/어드민 DTO 분리 위치: Controller 레벨

### 배경
브랜드/상품 정보의 고객용/어드민용 노출 필드가 다르다. 이 분리를 어느 레이어에서 처리할지 결정이 필요했다.

### 선택지
| 선택지 | 설명 |
|--------|------|
| **A. Controller/DTO 레벨** | Facade는 동일 Info 반환. Controller에서 DTO 변환 시 필드 필터링 |
| B. Facade/Info 레벨 | 다른 Info 객체 반환 (BrandInfo vs AdminBrandInfo) |

### 판단: A. Controller/DTO 레벨.

### 근거
- 고객/어드민 Facade가 분리되어 있지만, 같은 Service를 사용하므로 Info 객체는 동일.
- 노출 필드 차이는 표현(presentation) 관심사이므로 Controller/DTO 레벨이 적합.
- Info 객체 중복 방지.

### 트레이드오프
- DTO 변환 로직이 Controller에 존재 → ApiSpec(Swagger)과 함께 관리되므로 응집도 유지.

---

## 16. SOLD_OUT 상태 제거 및 soldOut 파생 필드

### 배경
ProductStatus에 SOLD_OUT이 있었으나, `stockQuantity == 0`과 동일한 정보를 표현한다. 또한 SOLD_OUT + 재고>0 (어드민 restock) 시 상태 불일치 문제가 발생한다.

### 판단: SOLD_OUT 제거. 고객 응답에 `soldOut: Boolean` 파생 필드 추가.

### 근거
- `stockQuantity == 0`이 이미 품절을 표현. 별도 상태는 중복.
- 상태 전환 관리 불필요 (deductStock에서 상태 변경 책임 제거).
- 고객에게 stockQuantity 미노출 유지. `soldOut = (stockQuantity == 0)` 파생 필드로 품절 전달.
- BrandStatus와 일관: 둘 다 ACTIVE / DELETED.
- 주문 시 재고 검증은 deductStock()이 최종 가드 (race condition 방어).

### 트레이드오프
- stock=0 상품이 고객 목록에 계속 노출 → 의도된 동작. 프론트에서 품절 뱃지 표시.

---

## 17. 좋아요/취소 양방향 멱등

### 배경
좋아요 등록/취소 시 이미 존재/부재한 경우의 처리 방식을 결정해야 했다. "내 좋아요 상품 조회" API와의 일관성이 핵심.

### 판단: 양방향 멱등. 항상 200 OK.

### 근거
- 좋아요 등록: INSERT → DuplicateKeyException catch → 200 OK. 결과 상태 "좋아요 있음"이면 성공.
- 좋아요 취소: DELETE → affected rows=0이어도 200 OK. 결과 상태 "좋아요 없음"이면 성공.
- 내 좋아요 목록은 product_like 실시간 조회 → like/unlike 즉시 반영.
- 좋아요 수(product.like_count)는 배치 갱신 → 약간의 딜레이 허용.

### 트레이드오프
- 클라이언트가 "실제로 등록/삭제되었는지" 구분 불가 → 비즈니스적으로 불필요.
- 예외 타입(DuplicateKeyException vs DataIntegrityViolationException) → 구현 시 결정.

---

## 18. Service 메서드 분리: 고객/어드민

### 배경
BrandService, ProductService, OrderService의 조회 메서드가 고객/어드민 Facade에서 공유되는데, DELETED 리소스에 대한 접근 정책이 다르다.

### 판단: Service 조회 메서드를 고객용(ACTIVE only)과 어드민용(상태 무관)으로 분리.

### 근거
- 고객: DELETED 브랜드/상품 → 404. 어드민: DELETED 포함 전체 조회 필요.
- 하나의 메서드에 status 파라미터를 추가하면 모든 호출부에서 인자를 전달해야 함.
- 메서드 분리가 추후 고객/어드민에 서로 다른 로직(캐싱, 조인 등)을 추가할 때 확장에 열림.
- 예: `getProduct(id)` (ACTIVE only) / `getProductForAdmin(id)` (상태 무관).
- 예: `getOrder(orderId, memberId)` (validateOwner) / `getOrderById(orderId)` (어드민, 검증 없음).

### 트레이드오프
- Service 메서드 수 증가 → 현재 단순 조회이므로 중복 부담 최소.

---

## 19. BR-B2 수정: 브랜드명 UNIQUE 제거

### 배경
`brand.name`에 UNIQUE 제약이 있었으나(BR-B2), soft delete와 충돌한다. DELETED 브랜드명을 재사용할 수 없다.

### 판단: 브랜드명 UNIQUE 제거. 중복 허용. 브랜드 식별은 PK(id) 기반.

### 근거
- name은 표시용 속성이지 식별자가 아님. 식별은 PK가 담당.
- soft delete + UNIQUE 시 삭제 후 동명 브랜드 재등록 불가.
- 변경 가능한 필드(수정 API 존재)에 UNIQUE는 불안정.
- MySQL에서 Partial Unique Index 미지원으로 workaround가 필요 → 복잡성 증가.
- UNIQUE 제거 후 `uk_brand_name` → `idx_brand_name` (일반 INDEX)으로 변경하여 검색 성능 유지.

### 트레이드오프
- 동명 브랜드 혼동 가능 → 어드민 UI에서 ID와 함께 표시하여 구분.

---

## 20. 좋아요 수 초기화: DEFAULT 0 (선삽입 제거)

### 배경
상품 등록 직후 좋아요순 정렬에서 해당 상품이 누락되지 않아야 한다.

### 판단: `product.like_count` 컬럼 DEFAULT 0으로 정의. 상품 INSERT 시 자동으로 0이 설정되므로 별도 초기화 불필요.

### 근거
- Decision 12에서 별도 집계 테이블을 제거하고 `product.like_count` 컬럼으로 통합.
- DEFAULT 0이므로 상품 등록 시 추가 INSERT/UPDATE 없이 자동 초기화.
- `AdminProductFacade → LikeService.initLikeCount()` 의존 제거 → cross-domain 결합 감소.
- 별도 집계 테이블 JOIN 불필요 → 쿼리 단순화.

### 트레이드오프
- 없음. 기존 선삽입 방식 대비 단순하고 결합도가 낮음.

---

## 21. DELETED 리소스 고객 접근 차단

### 배경
고객이 DELETED된 브랜드/상품에 직접 접근(단건 조회)할 때의 응답을 결정해야 했다.

### 판단: 404 Not Found. 브랜드/상품 동일 적용.

### 근거
- DELETED 리소스는 고객에게 "존재하지 않는" 리소스. 404가 자연스러움.
- 주문 목록은 스냅샷으로 노출 → 원본 상품 상태와 무관.
- 내 좋아요 목록은 ACTIVE 필터링 → DELETED 상품 자동 제외.
- 좋아요 등록 시 ProductService.getProduct() (ACTIVE only) → DELETED 상품 좋아요 자체 차단.

### 트레이드오프
- 외부 링크로 DELETED 상품 접근 시 404 → 프론트에서 "삭제된 상품입니다" 안내 필요.

---

## 22. 좋아요 데이터 유지 (브랜드/상품 삭제 시)

### 배경
브랜드 삭제 → 상품 soft delete 시 product_like 데이터 처리를 결정해야 했다.

### 판단: 좋아요 데이터 유지. 내 좋아요 목록에서 ACTIVE 필터링.

### 근거
- 마케팅/추천 데이터로 활용 가능 (유저 관심사 분석).
- product도 soft delete이므로 좋아요 데이터도 논리적으로 유효.
- 내 좋아요 목록: `product_like JOIN product WHERE status = 'ACTIVE'` → DELETED 자연 제외.
- 물리 삭제 시 복구 불가, 데이터 손실 위험.

### 트레이드오프
- DELETED 상품의 좋아요 데이터 축적 → 비즈니스 크리티컬하지 않음. 필요 시 배치 정리.

---

## 23. Value Object: @JvmInline value class (AttributeConverter 미사용)

### 배경
MemberModel의 `init` 블록에 loginId, name, email 검증 로직과 companion object에 regex/검증 메서드가 집중되어 생성자에 과도한 책임이 있었다. Value Object(VO) 패턴으로 검증 책임을 VO에 위임하고자 했다.

### 선택지
| 선택지 | 설명 |
|--------|------|
| A. `@JvmInline value class` + `@Convert` (AttributeConverter) | VO 타입으로 Entity 필드를 직접 매핑 |
| **B. `@JvmInline value class` + Entity 내부 primitive 저장** | 생성자에서 VO 수신, 필드는 `.value`로 추출하여 String 저장 |
| C. data class VO | 래핑 오버헤드 있음, Hibernate 매핑 복잡 |

### 판단: B. @JvmInline value class + Entity 내부 primitive 저장

### 근거
- **Hibernate 6.x 호환성 문제**: `@JvmInline value class`에 `@Convert(converter = XxxConverter::class)`를 적용하면 Hibernate 6.x에서 타입 매핑 오류가 발생한다. Kotlin의 inline class가 컴파일 시 언래핑되는 방식과 JPA AttributeConverter의 타입 추론이 충돌한다.
- **안전한 우회**: Entity 생성자가 VO 타입을 받고, 내부 필드는 `var loginId: String = loginId.value`로 primitive 저장. DB 복원 시에는 `LoginId(dbValue)` 생성자(검증 없음)로 복원.
- **검증 책임 분리**: `LoginId.of()`, `MemberName.of()`, `Email.of()` 팩토리에서 검증. Entity의 `init` 블록과 companion object에서 검증 로직 완전 제거.
- **zero overhead**: `@JvmInline`은 런타임에 래핑 객체를 생성하지 않으므로 성능 오버헤드 없음.

### 트레이드오프
- Entity 필드가 VO 타입이 아닌 primitive → VO의 타입 안전성이 Entity 내부에서는 적용되지 않음. 생성 경계(Service)에서 VO.of()를 통해 보장.
- DB 복원 시 검증 없는 생성자 사용 → DB에 이미 검증된 데이터만 존재하므로 문제 없음.
- Hibernate 버전 업그레이드 시 AttributeConverter 지원이 개선되면 재검토 가능.

### 적용 VO 목록
| VO | 패키지 | 검증 규칙 |
|----|--------|-----------|
| `LoginId` | `domain/member/vo/` | 영문+숫자만, 비어있지 않음 |
| `MemberName` | `domain/member/vo/` | 비어있지 않음 + `masked()` 메서드 |
| `Email` | `domain/common/vo/` | RFC-5322 regex |
| `RawPassword` | `domain/member/` | object (value class 아님). 8-16자, 허용문자, 생년월일 미포함 |

---

## 24. 주문 총액(totalAmount) 비정규화 제거

### 배경
OrderModel에 `totalAmount` 필드를 비정규화하여 저장할지, 주문 아이템의 `amount` 합으로 계산할지 결정이 필요했다.

### 선택지
| 선택지 | 설명 |
|--------|------|
| A. totalAmount 비정규화 저장 | orders 테이블에 컬럼 추가. INSERT 시 1회 계산 |
| **B. 매번 SUM 계산** | `getTotalAmount() = orderItems.sumOf { it.amount }` |

### 판단: B. 매번 SUM 계산 (비정규화 제거)

### 근거
- **주문당 아이템 수가 소규모**: 일반 이커머스에서 주문당 아이템 수는 1~10개 수준. 메모리 내 `sumOf` 연산은 나노초 단위로, DB 컬럼을 추가하여 얻는 이점이 거의 없다.
- **데이터 불일치 위험 제거**: 비정규화된 totalAmount와 orderItems.sumOf(amount)가 불일치할 가능성 자체를 제거.
- **단순성**: 파생 가능한 값을 별도 저장하지 않는 것이 데이터 모델의 정규화 원칙에 부합.
- **주문 목록 조회**: 주문 목록에서도 orderItems가 EAGER/JOIN으로 함께 로딩되므로 추가 쿼리 없이 계산 가능.

### 트레이드오프
- 주문 수만 건의 통계 쿼리 시 SUM 집계 필요 → 현재 스코프에서는 해당 유스케이스 없음. 필요 시 배치/집계 테이블로 대응.

---

## 25. 스냅샷 범위: 상품명·가격·브랜드명만 복사

### 배경
주문 시점의 상품 정보를 OrderItem에 스냅샷으로 저장하는 범위를 정의해야 했다. 어떤 필드를 스냅샷에 포함할지, 수량(quantity)과 금액(amount)의 성격을 명확히 구분할 필요가 있었다.

### 판단: productName, productPrice, brandName 3개 필드만 스냅샷

### 근거
- **스냅샷 대상**: 원본이 변경/삭제될 수 있는 참조 데이터 중, 주문 내역 표시에 필수적인 필드만 복사.
- **imageUrl, description 제외**: 주문 내역에서 이미지와 상세 설명은 필수 표시 항목이 아님. 필요 시 상품 조회 API로 대응 가능.
- **quantity는 주문 입력값**: 사용자가 지정한 수량이므로 스냅샷이 아닌 주문 고유 데이터.
- **amount는 파생값**: `productPrice × quantity`로 계산되는 값. 스냅샷 가격이 변하지 않으므로 재계산 결과도 불변.

### OrderItem 필드 역할 분류
| 필드 | 역할 | 설명 |
|------|------|------|
| productId | 참조 | 원본 상품 FK (논리적) |
| productName | 스냅샷 | 주문 시점 상품명 |
| productPrice | 스냅샷 | 주문 시점 단가 |
| brandName | 스냅샷 | 주문 시점 브랜드명 |
| quantity | 주문 입력 | 사용자 지정 수량 |
| amount | 파생값 | productPrice × quantity |

---

## 결론

모든 판단의 공통 원칙:

1. **현재 상태에 맞는 최소한의 변경**: 미래를 위한 과잉 설계보다 현재 병목을 해소하는 데 집중
2. **확장 가능한 설계**: 코드 변경 최소화로 향후 전환이 가능하도록 인터페이스(Spring Cache Abstraction, `@Transactional(readOnly)`) 기반 설계
3. **측정 기반 결정**: 추정이 아닌 실제 병목 지점을 기반으로 최적화 순서 결정
4. **운영 복잡성 최소화**: 추가 인프라 도입은 명확한 근거가 있을 때만 진행
5. **도메인 모델의 자기 보호**: 비즈니스 규칙(불변식, 접근 제어)은 도메인 모델 내부에서 검증
6. **요구사항만 구현**: 현재 스코프에 없는 기능은 미리 구현하지 않음 (YAGNI)
7. **기능 정합성 우선**: 동시성/멱등성/일관성은 기능 완성 후 별도 해결
