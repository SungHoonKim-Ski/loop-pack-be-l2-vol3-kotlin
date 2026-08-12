# Practical Read Optimization — 개념과 사례

> 발표 슬라이드(`read-optimization-slides.html`)의 핸드아웃.
> 이 발표의 절반은 **"DB는 왜 느린가"(PART 0)**이고, 나머지가 그걸 푸는 두 해법 — **쿼리(PART 1)**와 **캐싱(PART 2)**이다.
> 청중 가정: 주니어~미들 백엔드. 코드 사례는 `loop-pack-be-l2-vol3-kotlin`의 실제 구현.

## 주제 한눈에

| PART | 주제 | 핵심 |
|------|------|------|
| **0 · 토대** | DB는 왜 느린가 | 디스크·네트워크·쿼리 / 메모리 계층 / 저장장치 / DB 자체 캐시 |
| | 랜덤 액세스 오해 | "랜덤 = 빠름"이 아니다 — 전자식 vs 기계식 |
| | SSD vs RAM | 같은 전자식인데 SSD가 느린 4가지 이유 |
| | DB도 캐시를 쓴다 | 페이지 캐싱·WAL·OS 캐시는 공통 |
| | DB 캐시 차이 · 왜 Redis | 결과 캐싱은 공통이 아니라 외부화 |
| **1 · 쿼리** | C1 인덱스 | filesort·복합 인덱스·커버링·EXPLAIN |
| | C2 커서 페이징 | offset 비용 · keyset |
| **2 · 캐싱** | C3 캐시 전략 | cache-aside / write-through / write-behind |
| | C4 장애 격리 | graceful degradation · fail-open |
| | 캐시 도입의 트레이드오프 | 스탬피드 · 콜드 스타트 → 지터·싱글플라이트·워밍업 |

---

# PART 0 · DB는 왜 느린가 (토대)

## 왜 느린가 — DB는 왜 느리고, 캐시는 왜 빠른가

**DB 한 번 읽는 데 얹히는 비용** (글로벌 캐시보다도 느린 이유):
- **디스크 본체** — 데이터가 디스크(SSD/HDD)에. Redis는 순수 메모리.
- **쿼리 실행 + 버퍼풀 한계** — 파싱·플래닝·실행이 매번. InnoDB 버퍼풀(RAM)도 미스 시 디스크 random I/O. Redis는 키 조회 한 방.
- **네트워크도 '비용'** — 글로벌 캐시(Redis)든 DB든 네트워크 너머라 왕복(~수백 µs)이 똑같이 든다. DB만의 병목이 아니다. **로컬 캐시(Caffeine)만** 이마저 없앤다.

**그래서 — 자주 읽는 결과를 더 빠른 계층에 둔다:**
- **글로벌 캐시(Redis)** — 디스크 + 쿼리 실행을 없앤다. 네트워크는 남지만 메모리 조회라 DB보다 빠르고, 여러 인스턴스가 공유.
- **로컬 캐시(Caffeine)** — 네트워크 왕복마저 없앤다(~ns). 대신 인스턴스마다 사본이 따로 → **일관성 지점이 DB(1) → +Redis(2) → +로컬 N대(2+N)**로 늘어 무효화가 어려워진다(보통 짧은 TTL로 타협).

> 인덱스로 디스크를 덜 읽고(P1)·캐시로 메모리에 두고(P2) — 이 발표 전체가 "느린 계층을 덜 건드리는" 한 가지 이야기.

## 랜덤 액세스 오해 — "랜덤 액세스라서" 빠른 게 아니다

흔한 오해: **랜덤 액세스는 속도가 아니라 '접근 방식'**이다.
- **랜덤 액세스 = "임의 위치를 직접 지정"** — 반대말은 테이프식 순차 접근(10번 곡 들으려 1~9번 감기). CD가 10번을 바로 누르는 게 랜덤. 속도와 무관.
- **RAM이 빠른 진짜 이유 = 전자식** — 전기 신호로 닿는다(ns). 디스크(HDD)는 금속 헤드가 물리적으로 이동(ms). 전기 vs 기계 운동 = 100만 배 차이의 본질.
- **주소는 둘 다 안다** — 디스크도 "1234번 → 트랙·섹터"를 즉시 계산. 차이는 그 다음 — RAM은 주소 계산이 끝난 순간 접근 가능하지만, 디스크는 헤드 이동 + 회전 대기 시간이 걸린다.

비유: RAM = 책상 서랍(손만 뻗으면 닿음, 진짜 랜덤 액세스) / 디스크 = 회전목마 책장(칸 번호를 알아도 걸어가고 책장이 돌 때까지 기다려야).

## SSD vs RAM — 같은 전자식인데 SSD가 느린 이유

영속성을 얻은 대가로 속도를 내줬다 — 4가지:
1. **셀 재질** — DRAM은 전하로 잠깐 담음(물컵). NAND는 전자를 절연막에 가둬 비휘발성으로(금고). 가두고 빼기가 느리다.
2. **덮어쓰기 불가** — 쓰기는 페이지(4~16KB), 지우기는 더 큰 블록. 한 글자 수정도 "블록 읽기→새로 쓰기→옛 블록 지우기"(GC). 펜으로만 쓰는 공책.
3. **컨트롤러** — FTL(주소 변환)·웨어레벨링·GC라는 중간 관리자. RAM엔 없음.
4. **I/O 통로** — PCIe/SATA + OS 파일시스템·드라이버 계층. CPU 버스 직결인 RAM보다 단계가 많다.

| | 닿는 방식 | 속도 | 영속 | 한 글자 수정 |
|---|----------|------|------|--------------|
| RAM | 전기·CPU 직결 | ns | ✗ 휘발 | 바로 |
| SSD | 전기(NAND)·컨트롤러+I/O | µs | ✓ | 블록 단위 |
| HDD | 금속 헤드 물리 이동 | ms | ✓ | 덮어쓰기 |

→ RAM은 속도를 얻고 영속성을 포기, 디스크는 영속성을 얻고 속도를 내줌. 그래서 계층으로 쌓는다.

## DB도 캐시를 쓴다 — 공통 뼈대

같은 물리 제약(RAM vs 디스크)을 푸니 해법이 수렴한다. 이름만 다를 뿐:
- **① 페이지 캐싱 (Buffer Pool 계열)** — 데이터·인덱스 페이지를 RAM에. MySQL `Buffer Pool`(16KB) / PostgreSQL `Shared Buffers`(8KB) / Oracle `Buffer Cache` / SQL Server `Buffer Pool` / MongoDB `WiredTiger Cache` / SQLite `Page Cache`. ("DB 메모리 튜닝 = 이 캐시 크기 잡기")
- **② 쓰기는 WAL 먼저 (Write-Ahead Logging)** — 데이터 파일(디스크)에 바로 쓰면 여기저기 흩어진 랜덤 쓰기라 느리다. 그래서 ① RAM 버퍼에서 바꾸고 ② 변경분만 로그 끝에 순차로 append(빠름)해 영속성을 먼저 확보, 데이터 파일은 나중에 모아서 반영(장애 시 로그로 복구). redo log(MySQL·Oracle) / WAL(PostgreSQL, 원조) / transaction log(SQL Server) / journal(MongoDB).
- **③ 그 아래 OS page cache** — DB 종류와 무관하게 디스크 파일을 읽으면 OS가 한 번 더 캐싱.

## DB 캐시 — 차이와 왜 Redis

**다른 부분:**
- **OS 캐시를 믿느냐, 직접 관리하느냐** — PostgreSQL은 `shared_buffers`를 작게 두고 OS page cache에 위임(~25%). Oracle·InnoDB(O_DIRECT)는 OS 우회·직접 관리(~50–70%). 같은 페이지 캐싱인데 역할 분담이 정반대.
- **교체 정책** — 다 LRU 계열이나 변형 제각각(InnoDB young/old, PostgreSQL clock sweep).

**그래서 왜 Redis인가:**
- **결과 캐싱은 공통이 아니다** — MySQL Query Cache는 8.0에서 제거(무효화·경합). 페이지 캐시는 디스크 I/O만 줄일 뿐 파싱·실행은 매번 → 현대 DB는 결과 캐싱을 앱(Redis)으로 외부화.
- **in-memory DB는 트레이드오프를 뒤집음** — Redis·SAP HANA는 RAM이 본진, 디스크는 백업(스냅샷·AOF)용.

→ 공통 = 페이지 캐싱·WAL·OS 캐시. 다름 = OS 의존도·교체 정책·**결과 캐싱**. DB가 안 하는 결과 캐싱, 그 자리를 Redis가 채운다.

---

# PART 1 · 쿼리가 DB를 덜 일하게

## C1. 인덱스로 정렬까지 끝내기

**개념** (복합 인덱스 → 커버링 → EXPLAIN → filesort 순):
- **① 복합 인덱스** — 여러 컬럼을 묶은 B+Tree. 리프에 컬럼 순서대로 정렬 저장 → 선두로 진입해 순차 스캔하면 그게 곧 정렬 결과(추가 정렬 0). 규칙: 등치(`=`) 필터 먼저, 정렬키 뒤. (leftmost — 왼쪽부터 연속으로 써야 먹는다)
- **② 커버링 인덱스** — 필요한 컬럼이 전부 인덱스 안에 있으면 PK 본체를 안 읽음(`Using index`). `SELECT *`는 빠진 컬럼 때문에 행마다 PK lookup → 커버링 깨짐.
- **③ EXPLAIN** — 쿼리가 인덱스를 타는지 보는 창. `key`(실제 쓴 인덱스), `Extra`(`Using index`=커버링 / `Using filesort`=재정렬).
- **④ filesort** — 인덱스로 `ORDER BY`를 못 풀면 sort buffer(크면 디스크)에서 다시 정렬. `LIMIT 20`이어도 매칭 행 전체가 정렬 대상이라 비싸다 → **이게 안 뜨게** 만드는 게 목표.

**사례 — 상품 목록 (🧭 D38):**
```sql
-- Before(단독 인덱스): WHERE brand_id=? AND status='ACTIVE' ORDER BY like_count DESC
--   EXPLAIN: Using where; Using filesort
-- After(복합 인덱스):
CREATE INDEX idx_product_brand_status_like
  ON product (brand_id, status, like_count DESC, id DESC);
--   EXPLAIN: Using where  (filesort 제거)
```
`id DESC` tie-breaker로 커서(C2)와 호환. 함정: `like_count DESC, id ASC`처럼 방향이 어긋나면 filesort 부활. (D38: 단독 인덱스 유지 vs 복합 2개 → 복합 채택)

## C2. 페이지가 깊어져도 일정한 비용 — 커서 페이징

**개념:**
- **offset의 비용** — `LIMIT 100000, 20`은 앞 10만 행을 실제로 읽어 센 뒤 버린다 → `O(offset)`. 페이지 사이 행이 밀리면 중복/누락.
- **커서(keyset) 페이징** — "마지막 본 행의 키"를 다음 쿼리 `WHERE` 조건으로. offset 없이 인덱스 레인지 진입 → 깊이와 무관하게 일정 비용. 단 임의 페이지 점프 불가.
- **튜플 비교 + tie-breaker** — `(like_count, id) < (42, 1080)`이 C1 복합 인덱스와 맞물려 레인지 스캔 한 번. `id`는 같은 정렬값 사이 순서를 고정.

**사례 — 대고객 상품 목록 (🧭 D28):**
```kotlin
// 정렬 기준별 커서 맵 — 항상 id를 tie-breaker로, Base64 인코딩
sealed interface ProductCursor {
    data class Latest(val id: Long) : ProductCursor
    data class PriceAsc(val price: Long, val id: Long) : ProductCursor
    data class LikesDesc(val likeCount: Int, val id: Long) : ProductCursor
}
```
적용 범위 한정 — 앞으로만 가는 대고객 목록만 커서, "N페이지 점프"가 필요한 어드민·주문은 offset 유지.

---

# PART 2 · 캐싱

## C3. 같은 질문은 캐시로 — 읽기·쓰기 전략

읽기는 대개 cache-aside로 같지만, **쓰기를 어떻게 반영하느냐**가 갈린다.
- **cache-aside** (이 프로젝트) — 앱이 직접. 읽기: 캐시 먼저, miss면 DB 후 적재. 쓰기: DB 갱신 후 캐시 무효화(evict/TTL).
- **write-through** — 쓰기가 캐시를 거쳐 DB로 같이. 캐시·DB 항상 일치(강한 일관성), 대신 쓰기 느림.
- **write-behind(write-back)** — 쓰기는 캐시에만 먼저, DB는 나중 비동기. 쓰기 가장 빠름, 대신 반영 전 장애 시 유실.

| 전략 | 일관성 | 쓰기 | 위험 |
|------|--------|------|------|
| cache-aside | 보통(잠깐 stale) | 빠름 | 무효화 누락 |
| write-through | 강(항상 일치) | 느림 | — |
| write-behind | 약(지연) | 가장 빠름 | 장애 시 유실 |

**사례 (🧭 D37):** 이 프로젝트는 cache-aside — DB 갱신 후 무효화(상세·참조는 evict, 목록은 키 조합이 무한해 짧은 TTL). 강한 일관성보다 단순성·읽기 성능. `@Cacheable` 대신 RedisTemplate + Port 패턴(DIP).

## C4. 캐시가 죽어도 서비스는 산다 — 장애 격리

**개념:**
- **graceful degradation** — 일부 구성요소가 죽어도 전체가 멈추지 않고 기능/성능만 저하. 캐시는 새 의존성이므로 그 장애를 서비스 장애와 분리해야.
- **fail-open vs fail-closed** — 같은 "실패 처리"라도 방향이 반대. 보조 장치(캐시)는 실패 시 통과(fail-open), 게이트(인증·결제)는 실패 시 차단(fail-closed).
- **읽기 분산** — 읽기 Replica, 쓰기 Master. 대신 복제 지연(lag).

**사례 (🧭 D37 · D41):**
```kotlin
override fun getProduct(id: Long): ProductInfo? = try {
    redisTemplate.opsForValue().get("$KEY$id")?.let { decode(it) }
} catch (e: Exception) {
    log.warn("cache 조회 실패 (id={})", id, e)
    null   // fail-open: 캐시 죽어도 DB fallback
}
```
실패를 조용히 삼키므로 반드시 `log.warn`으로 가시화. 읽기 redisTemplate(Replica)/쓰기 masterRedisTemplate(Master) 분리.

## 캐시 도입의 트레이드오프

캐시는 저장소(상태)가 하나 더 느는 것 — 공짜가 아니다.
- **비용** — 일관성(stale·무효화), 미스 페널티(히트율 낮으면 오히려 손해), 메모리·eviction.
- **두 고질병** — **캐시 스탬피드**(인기 키 만료 순간 동시 미스 → DB 폭주), **콜드 스타트**(빈 캐시 → 전부 미스).

**캐시 스탬피드 — 전략과 구현:**
```kotlin
// ① 지터(jitter) — 만료 시각을 흩어 동시 만료 방지
val ttl = BASE_TTL.plusSeconds(Random.nextLong(0, JITTER_SEC))
master.opsForValue().set(key, value, ttl)

// ② 싱글플라이트 — 같은 키 동시 요청을 1번 로딩으로 (Go singleflight 직접 구현)
fun getProduct(id: Long): Product {
    cache.get(id)?.let { return it }            // 캐시 히트면 끝
    return singleFlight.run("product:$id") {     // 미스 → 같은 키는 1팀으로
        repo.findById(id).also { cache.put(id, it) }   // 대표 1명만 DB→캐시
    }
}
class SingleFlight<V> {
    private val calls = ConcurrentHashMap<String, CompletableFuture<V>>()
    fun run(key: String, loader: () -> V): V =
        calls.computeIfAbsent(key) {             // 첫 호출만 실행, 나머진 같은 걸 받음
            CompletableFuture.supplyAsync(loader)
                .whenComplete { _, _ -> calls.remove(key) }
        }.join()
}
```

**콜드 스타트 — 전략과 구현:**
```kotlin
// 워밍업(pre-warm) — 오픈 전 인기 데이터를 미리 적재
@Component
class CacheWarmer(...) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        popularProductIds().forEach { id -> cacheStore.put(id, productService.getProduct(id)) }
    }
}
```
싱글플라이트는 콜드 스타트의 동시 미스도 1회 로딩으로 묶어 완화(스탬피드와 같은 도구).

---

## 부록. 라이브 데모 후보

| 보여줄 것 | 방법 |
|-----------|------|
| filesort 유무 (C1) | `EXPLAIN SELECT ... ORDER BY like_count DESC` 전/후 → `Using filesort` |
| offset vs 커서 (C2) | `OFFSET 100000` 쿼리와 커서 쿼리 실행시간 비교 |
| 캐시 hit/miss (C3) | 같은 상품 2회 조회 → 2번째 DB 로그 없음 / `redis-cli MONITOR` |
| 메모리 계층 | `redis-cli` 메모리 조회 vs DB 디스크 조회 레이턴시 |

## 부록. 한 장 요약 — 읽기 최적화 사고 순서

1. **측정부터** — EXPLAIN/슬로우쿼리/메트릭. 추측 인덱스 금지.
2. **DB가 왜 느린지 이해** — 디스크·네트워크·쿼리. 느린 계층을 덜 건드리는 게 전부 (PART 0).
3. **DB가 덜 일하게** — 인덱스·커서로 filesort·deep offset 제거 (PART 1).
4. **같은 질문 반복 차단** — 캐시. 단 fallback(fail-open)·무효화·트레이드오프(스탬피드/콜드스타트)를 한 세트로 (PART 2).
5. **모든 선택에 트레이드오프(주로 stale)가 있다** — 무엇을 얼마나 양보할지 정하고 기록.
