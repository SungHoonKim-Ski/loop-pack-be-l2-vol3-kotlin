# 01. 요구사항 명세

---

## 1. 서비스 개요

감성 이커머스 — 좋아요 누르고, 쿠폰 쓰고, 주문 및 결제하는 서비스.
브랜드별 상품을 둘러보고, 마음에 드는 상품에 좋아요를 누르고, 여러 상품을 한 번에 주문합니다.

### 서비스 흐름

1. 사용자가 **회원가입**을 한다.
2. 여러 **브랜드의 상품**을 둘러보고, 마음에 드는 상품엔 **좋아요**를 누른다.
3. 여러 상품을 **한 번에 주문**한다.
4. 회원의 행동은 모두 기록되고, 이후 다양한 기능(랭킹, 추천, 쿠폰, 결제)으로 확장된다.

### API 접근 체계

| 구분 | Prefix | 인증 방식 |
|------|--------|-----------|
| 대고객 (비인증) | `/api/v1` | 없음 |
| 대고객 (인증) | `/api/v1` | `X-Loopers-LoginId` + `X-Loopers-LoginPw` |
| 어드민 | `/api-admin/v1` | `X-Loopers-Ldap: loopers.admin` |

---

## 2. 용어 정의 (Ubiquitous Language)

| 한글 | 영문 | 설명 |
|------|------|------|
| 회원 | Member | 서비스에 가입한 사용자 |
| 브랜드 | Brand | 상품을 제공하는 브랜드 |
| 상품 | Product | 브랜드에 속한 판매 상품 |
| 좋아요 | Like | 회원이 상품에 표시하는 관심 표현 |
| 좋아요 수 | Like Count | 상품별 좋아요 집계. 배치로 주기적 갱신되며 실시간 정확성은 보장하지 않음 (eventual consistency) |
| 주문 | Order | 회원이 상품을 구매하는 행위 |
| 주문 상품 | Order Item | 주문에 포함된 개별 상품 (스냅샷) |
| 스냅샷 | Snapshot | 주문 시점의 상품 정보 복사본 |
| 품절 | Sold Out | 재고가 0인 상태 |
| 소프트 삭제 | Soft Delete | 물리 삭제 대신 상태를 DELETED로 변경 |

### 상태값 정의

| 도메인 | 상태 | 설명 |
|--------|------|------|
| 브랜드 | ACTIVE | 활성 (고객 노출) |
| 브랜드 | DELETED | 삭제됨 (고객 미노출) |
| 상품 | ACTIVE | 활성 (고객 노출, 품절 포함) |
| 상품 | DELETED | 삭제됨 (고객 미노출) |
| 주문 | ORDERED | 주문 완료 |
| 주문 | CANCELLED | 주문 취소 |

---

## 3. 도메인별 요구사항

### 3.1 회원 (Users) — 1주차 완료

> 설계 범위 제외. 이미 구현 완료.

| METHOD | URI | 인증 | 설명 |
|--------|-----|------|------|
| POST | `/api/v1/members` | X | 회원가입 |
| GET | `/api/v1/members/me` | O (`@MemberAuthenticated`) | 내 정보 조회 |
| PATCH | `/api/v1/members/me/password` | O (`@MemberAuthenticated`) | 비밀번호 변경 |

---

### 3.2 브랜드 (Brands)

#### 유저 시나리오

> "사용자는 관심 있는 브랜드의 정보를 확인하고, 해당 브랜드의 상품을 둘러본다."

#### 대고객 API

| METHOD | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api/v1/brands/{brandId}` | X | 브랜드 정보 조회 |

#### 어드민 API

| METHOD | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api-admin/v1/brands?page=0&size=20` | LDAP | 브랜드 목록 조회 (페이징) |
| GET | `/api-admin/v1/brands/{brandId}` | LDAP | 브랜드 상세 조회 |
| POST | `/api-admin/v1/brands` | LDAP | 브랜드 등록 |
| PUT | `/api-admin/v1/brands/{brandId}` | LDAP | 브랜드 정보 수정 |
| DELETE | `/api-admin/v1/brands/{brandId}` | LDAP | 브랜드 삭제 |

#### 비즈니스 규칙

- **BR-B1**: 브랜드 삭제 시, 해당 브랜드의 모든 상품도 함께 삭제되어야 한다.
- **BR-B2**: 브랜드 식별은 ID(PK) 기반이다. 브랜드명 중복을 허용한다. (soft delete + UNIQUE 충돌 방지)

#### 입력값 검증

| 필드 | 제약 | 필수 |
|------|------|------|
| name | 1~255자 | O |
| description | 최대 1000자 | X |
| imageUrl | 유효한 URL 형식, 최대 512자 | X |

#### 고객/어드민 정보 분리

| 필드 | 고객 노출 | 어드민 노출 |
|------|-----------|-------------|
| id | O | O |
| name | O | O |
| description | O | O |
| imageUrl | O | O |
| status | X | O |
| createdAt | X | O |
| updatedAt | X | O |

> 고객에게는 활성(ACTIVE) 상태의 브랜드만 노출한다. 어드민은 상태와 관리 일시를 포함한 전체 정보를 조회할 수 있다. (설계 근거: DECISIONS.md #15)

#### 응답 형식

**고객 브랜드 조회 응답**
```json
{
  "id": 1,
  "name": "루퍼스",
  "description": "감성 브랜드",
  "imageUrl": "https://example.com/brand.jpg"
}
```

**어드민 브랜드 조회 응답**
```json
{
  "id": 1,
  "name": "루퍼스",
  "description": "감성 브랜드",
  "imageUrl": "https://example.com/brand.jpg",
  "status": "ACTIVE",
  "createdAt": "2026-01-01T00:00:00+09:00",
  "updatedAt": "2026-01-15T12:00:00+09:00"
}
```

#### 유스케이스 흐름

**고객 브랜드 조회**

```
[Main Flow]
1. 사용자가 브랜드 상세 정보를 요청한다.
2. 브랜드 정보(이름, 설명, 이미지)를 반환한다.

[Exception Flow]
E1. 삭제된 브랜드인 경우 → 404 Not Found
E2. 존재하지 않는 브랜드인 경우 → 404 Not Found
```

**어드민 브랜드 삭제**

```
[Main Flow]
1. 어드민이 브랜드 삭제를 요청한다.
2. 해당 브랜드의 모든 상품이 삭제 처리된다. (BR-B1)
3. 브랜드가 삭제 처리된다.
4. 200 OK를 반환한다.

[Alternate Flow]
A1. 삭제된 상품의 좋아요 데이터는 유지된다. (마케팅 활용)
A2. 기존 주문의 스냅샷에는 영향이 없다.

[Exception Flow]
E1. 존재하지 않는 브랜드인 경우 → 404 Not Found
```

---

### 3.3 상품 (Products)

#### 유저 시나리오

> "사용자는 상품 목록을 브랜드별로 필터링하고, 정렬하여 둘러본다. 마음에 드는 상품의 상세 정보를 확인한다."

#### 대고객 API

| METHOD | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api/v1/products?brandId=1&sort=latest&page=0&size=20` | X | 상품 목록 조회 |
| GET | `/api/v1/products/{productId}` | X | 상품 정보 조회 |

#### 상품 목록 조회 쿼리 파라미터

| 파라미터 | 예시 | 설명 | 필수 |
|----------|------|------|------|
| brandId | 1 | 특정 브랜드의 상품만 필터링 | X |
| sort | `latest` / `price_asc` / `likes_desc` | 정렬 기준 (기본값: `latest`). 2차 정렬: `id DESC` | X |
| page | 0 | 페이지 번호 (기본값: 0) | X |
| size | 20 | 페이지당 상품 수 (기본값: 20) | X |

#### 어드민 API

| METHOD | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api-admin/v1/products?page=0&size=20&brandId={brandId}` | LDAP | 상품 목록 조회 |
| GET | `/api-admin/v1/products/{productId}` | LDAP | 상품 상세 조회 |
| POST | `/api-admin/v1/products` | LDAP | 상품 등록 |
| PUT | `/api-admin/v1/products/{productId}` | LDAP | 상품 정보 수정 |
| DELETE | `/api-admin/v1/products/{productId}` | LDAP | 상품 삭제 |

#### 비즈니스 규칙

- **BR-P1**: 상품 등록 시, 상품의 브랜드는 이미 등록된 브랜드여야 한다.
- **BR-P2**: 상품의 브랜드는 등록 후 수정할 수 없다.
- **BR-P3**: 상품 가격은 0 이상이어야 한다.
- **BR-P4**: 상품 재고는 0 이상이어야 한다.

#### 입력값 검증

| 필드 | 제약 | 필수 |
|------|------|------|
| brandId | 존재하는 브랜드 ID (BR-P1) | O |
| name | 1~255자 | O |
| description | 최대 1000자 | X |
| price | 0 이상의 정수 (BR-P3) | O |
| stockQuantity | 0 이상의 정수 (BR-P4) | O |
| imageUrl | 유효한 URL 형식, 최대 512자 | X |

#### 고객/어드민 정보 분리

| 필드 | 고객 노출 | 어드민 노출 |
|------|-----------|-------------|
| id | O | O |
| brandId / brandName | O | O |
| name | O | O |
| description | O | O |
| price | O | O |
| imageUrl | O | O |
| likeCount | O | O |
| soldOut | O | X |
| stockQuantity | X | O |
| status | X | O |
| createdAt | X | O |
| updatedAt | X | O |

> 재고(stockQuantity)와 상태(status)는 운영 정보이므로 고객에게 노출하지 않는다. 좋아요 수(likeCount)는 고객 경험에 필요하므로 노출한다. 품절 여부(soldOut)는 재고가 0인지 여부를 나타내는 필드로, 고객에게만 노출한다. (설계 근거: DECISIONS.md #15, #21)

#### 응답 형식

**고객 상품 조회 응답**
```json
{
  "id": 1,
  "brandId": 1,
  "brandName": "루퍼스",
  "name": "감성 티셔츠",
  "description": "편안한 착용감",
  "price": 39000,
  "imageUrl": "https://example.com/product.jpg",
  "likeCount": 42,
  "soldOut": false
}
```

**어드민 상품 조회 응답**
```json
{
  "id": 1,
  "brandId": 1,
  "brandName": "루퍼스",
  "name": "감성 티셔츠",
  "description": "편안한 착용감",
  "price": 39000,
  "imageUrl": "https://example.com/product.jpg",
  "likeCount": 42,
  "stockQuantity": 100,
  "status": "ACTIVE",
  "createdAt": "2026-01-01T00:00:00+09:00",
  "updatedAt": "2026-01-15T12:00:00+09:00"
}
```

#### 유스케이스 흐름

**고객 상품 상세 조회**

```
[Main Flow]
1. 사용자가 상품 상세 정보를 요청한다.
2. 상품 정보(이름, 설명, 가격, 이미지, 좋아요 수, 품절 여부)를 반환한다.

[Exception Flow]
E1. 삭제된 상품인 경우 → 404 Not Found
E2. 존재하지 않는 상품인 경우 → 404 Not Found
```

**어드민 상품 등록**

```
[Main Flow]
1. 어드민이 상품 정보(브랜드, 이름, 설명, 가격, 재고, 이미지)를 입력하여 등록을 요청한다.
2. 브랜드 존재 여부를 확인한다. (BR-P1)
3. 상품이 등록된다.
4. 좋아요 집계가 0으로 초기화된다.
5. 201 Created를 반환한다.

[Exception Flow]
E1. 존재하지 않는 브랜드인 경우 → 404 Not Found
E2. 가격이 0 미만인 경우 → 400 Bad Request (BR-P3)
E3. 재고가 0 미만인 경우 → 400 Bad Request (BR-P4)
```

---

### 3.4 좋아요 (Likes)

#### 유저 시나리오

> "사용자는 마음에 드는 상품에 좋아요를 누르고, 나중에 좋아요한 상품 목록을 다시 확인한다."

#### 대고객 API

| METHOD | URI | 인증 | 설명 |
|--------|-----|------|------|
| POST | `/api/v1/products/{productId}/likes` | O | 상품 좋아요 등록 |
| DELETE | `/api/v1/products/{productId}/likes` | O | 상품 좋아요 취소 |
| GET | `/api/v1/users/{userId}/likes` | O | 내가 좋아요한 상품 목록 조회 (페이징 없음) |

> URI의 `users`는 API 경로 규칙이며, 도메인 용어 "회원(Member)"과 동일한 대상을 지칭한다.

#### 비즈니스 규칙

- **BR-L1**: 동일 상품에 중복 좋아요 불가. (memberId + productId 유니크)
- **BR-L2**: 좋아요 등록/취소는 멱등하게 동작한다. (이미 좋아요 → 성공, 좋아요 없이 취소 → 성공)
- **BR-L3**: 좋아요한 상품 목록은 본인의 것만 조회 가능하다. (userId = 인증된 사용자)
- **BR-L4**: 존재하지 않는 상품에 좋아요할 수 없다.

#### 유스케이스 흐름

**좋아요 등록**

```
[Main Flow]
1. 회원이 상품에 좋아요를 요청한다.
2. 상품 존재 여부를 확인한다. (BR-L4)
3. 좋아요가 등록된다.
4. 200 OK를 반환한다.

[Alternate Flow]
A1. 이미 좋아요한 상품인 경우 → 200 OK (멱등, BR-L2)

[Exception Flow]
E1. 로그인하지 않은 경우 → 401 Unauthorized
E2. 존재하지 않는 상품인 경우 → 404 Not Found
E3. 삭제된 상품인 경우 → 404 Not Found
```

**좋아요 취소**

```
[Main Flow]
1. 회원이 상품 좋아요 취소를 요청한다.
2. 좋아요가 삭제된다.
3. 200 OK를 반환한다.

[Alternate Flow]
A1. 좋아요하지 않은 상품인 경우 → 200 OK (멱등, BR-L2)

[Exception Flow]
E1. 로그인하지 않은 경우 → 401 Unauthorized
```

**내 좋아요 목록 조회**

```
[Main Flow]
1. 회원이 자신의 좋아요 상품 목록을 요청한다.
2. 활성(ACTIVE) 상품만 필터링하여 반환한다.

[Alternate Flow]
A1. 삭제된 상품의 좋아요는 목록에서 자동 제외된다.

[Exception Flow]
E1. 로그인하지 않은 경우 → 401 Unauthorized
E2. 본인의 목록이 아닌 경우 → 403 Forbidden (BR-L3)
```

#### 응답 형식

**좋아요한 상품 목록 응답**
```json
[
  {
    "productId": 1,
    "productName": "감성 티셔츠",
    "price": 39000,
    "imageUrl": "https://example.com/product.jpg",
    "brandName": "루퍼스"
  }
]
```

---

### 3.5 주문 (Orders)

#### 유저 시나리오

> "사용자는 여러 상품을 선택하고 수량을 지정하여 한 번에 주문한다. 주문 이력을 기간별로 조회하고, 상세 내역을 확인한다."

#### 대고객 API

| METHOD | URI | 인증 | 설명 |
|--------|-----|------|------|
| POST | `/api/v1/orders` | O | 주문 요청 |
| GET | `/api/v1/orders?startAt=2026-01-31&endAt=2026-02-10` | O | 주문 목록 조회 (기간 필터, inclusive, 페이징 없음) |
| GET | `/api/v1/orders/{orderId}` | O | 단일 주문 상세 조회 |

#### 주문 요청 예시

```json
{
  "items": [
    { "productId": 1, "quantity": 2 },
    { "productId": 3, "quantity": 1 }
  ]
}
```

#### 주문 생성 응답 예시

```json
{
  "orderId": 1,
  "status": "ORDERED",
  "totalAmount": 117000,
  "orderedAt": "2026-02-10T14:30:00+09:00",
  "items": [
    {
      "productName": "감성 티셔츠",
      "brandName": "루퍼스",
      "price": 39000,
      "quantity": 2,
      "amount": 78000
    },
    {
      "productName": "미니백",
      "brandName": "루퍼스",
      "price": 39000,
      "quantity": 1,
      "amount": 39000
    }
  ]
}
```

#### 어드민 API

| METHOD | URI | 인증 | 설명 |
|--------|-----|------|------|
| GET | `/api-admin/v1/orders?page=0&size=20` | LDAP | 주문 목록 조회 (페이징) |
| GET | `/api-admin/v1/orders/{orderId}` | LDAP | 단일 주문 상세 조회 |

#### 비즈니스 규칙

- **BR-O1**: 주문 시 상품 재고를 확인하고, 충분한 경우에만 재고를 차감한다.
- **BR-O2**: 주문 정보에는 당시의 상품 정보가 스냅샷으로 저장되어야 한다. (상품명, 가격, 브랜드명 등)
- **BR-O3**: 주문은 최소 1개 이상의 상품을 포함해야 한다.
- **BR-O4**: 주문 수량은 1 이상이어야 한다.
- **BR-O5**: 유저는 자신의 주문만 조회할 수 있다.
- **BR-O6**: 재고 부족 시 주문은 실패해야 한다.

#### 입력값 검증

| 필드 | 제약 | 필수 |
|------|------|------|
| items | 1개 이상의 주문 상품 (BR-O3) | O |
| items[].productId | 존재하는 활성 상품 ID | O |
| items[].quantity | 1 이상의 정수 (BR-O4) | O |

#### 주문 상태

| 상태 | 설명 |
|------|------|
| ORDERED | 주문 완료 (결제 기능 추가 전 최종 상태) |
| CANCELLED | 주문 취소 (현재 스코프 외. 향후 확장을 위해 사전 정의) |

> **확장 포인트**: 결제 기능 추가 시 `PAID`, `DELIVERING`, `DELIVERED`, `COMPLETED` 등으로 확장 가능.

#### 유스케이스 흐름

**주문 생성**

```
[Main Flow]
1. 회원이 상품 목록(상품ID, 수량)으로 주문을 요청한다.
2. 요청된 상품들의 존재 여부와 활성 상태를 확인한다.
3. 각 상품의 재고를 확인하고 차감한다. (BR-O1)
4. 주문 시점의 상품 정보(상품명, 가격, 브랜드명)를 스냅샷으로 저장한다. (BR-O2)
5. 주문이 생성되고 201 Created를 반환한다.

[Exception Flow]
E1. 로그인하지 않은 경우 → 401 Unauthorized
E2. 상품이 1개 미만인 경우 → 400 Bad Request (BR-O3)
E3. 수량이 1 미만인 경우 → 400 Bad Request (BR-O4)
E4. 삭제된 상품이 포함된 경우 → 404 Not Found
E5. 재고가 부족한 경우 → 400 Bad Request (BR-O6)
```

**고객 주문 상세 조회**

```
[Main Flow]
1. 회원이 주문 상세 정보를 요청한다.
2. 본인 주문 여부를 확인한다. (BR-O5)
3. 주문 정보와 스냅샷 상품 정보를 반환한다.

[Exception Flow]
E1. 로그인하지 않은 경우 → 401 Unauthorized
E2. 본인의 주문이 아닌 경우 → 403 Forbidden
E3. 존재하지 않는 주문인 경우 → 404 Not Found
```

**어드민 주문 상세 조회**

```
[Main Flow]
1. 어드민이 주문 상세 정보를 요청한다.
2. 주문 정보와 스냅샷 상품 정보를 반환한다. (본인 검증 없음)

[Exception Flow]
E1. 존재하지 않는 주문인 경우 → 404 Not Found
```

---

## 4. 공통 제약사항

| 제약 | 설명 |
|------|------|
| 인증/인가 미구현 | 헤더 기반 식별만 수행, 권한 체계는 스코프 밖 |
| JWT 미사용 | `X-Loopers-LoginId` / `X-Loopers-LoginPw` 헤더 기반 |
| 어드민 인증 | `X-Loopers-Ldap: loopers.admin` 헤더 기반 |
| 결제 미구현 | 추후 별도로 개발 예정 |
| 동시성/멱등성 | 기능 완성 후 별도로 해결 예정 |

#### 공통 에러 응답 형식

```json
{
  "code": "NOT_FOUND",
  "message": "상품을 찾을 수 없습니다."
}
```

| 상태 코드 | code | 상황 |
|-----------|------|------|
| 400 | BAD_REQUEST | 요청 파라미터 오류, 비즈니스 규칙 위반 |
| 401 | UNAUTHORIZED | 인증 정보 없음 또는 인증 실패 |
| 403 | FORBIDDEN | 권한 없음 (본인 리소스가 아닌 경우) |
| 404 | NOT_FOUND | 리소스 미존재 또는 삭제됨 |

---

## 4.1 비기능 요구사항

| 구분 | 요구사항 | 비고 |
|------|---------|------|
| 성능 | Phase 1: 기능 정합성 우선. Phase 2: 동시성/멱등성. Phase 3: 10,000 TPS (피크 기준) | 상세: REQUIREMENTS.md 참조 |
| 보안 | 비밀번호: 8~16자, 대소문자+숫자+특수문자 포함. 평문 저장 금지 (BCrypt) | |
| 데이터 정합성 | 주문 시 재고 차감은 원자적으로 수행. 좋아요 수는 배치 기반 eventual consistency 허용 | |
| 응답 시간 | 목표 미확정 (Phase 3에서 설정) | |

---

## 5. 향후 확장 포인트

| 기능 | 설명 | 연관 도메인 |
|------|------|-------------|
| 결제 | 주문에 대한 결제 처리 | Order |
| 쿠폰 | 쿠폰 발급, 적용, 할인 | Order, Product |
| 랭킹 | 좋아요/주문 기반 인기 상품 | Product, Like, Order |
| 추천 | 유저 행동 기반 상품 추천 | Like, Order |
| 장바구니 | 주문 전 상품 담기 | Product |
