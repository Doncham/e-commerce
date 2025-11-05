# 📦 E-Commerce ERD 설명서

이 문서는 `user`, `product`, `order`, `payment`, `coupon`을 중심으로 하는 간단한 커머스 도메인의 ERD 구조를 설명합니다.  
아래는 테이블별 컬럼 정의와 관계를 정리한 것입니다.

---
# 엔티티 설명
## 1. user (회원)

| 컬럼명        | 타입         | 설명             | 비고                         |
|---------------|--------------|------------------|------------------------------|
| id            | bigint (PK)  | 사용자 ID        | AUTO_INCREMENT               |
| email         | varchar(255) | 이메일(로그인)   | **UNIQUE, NOT NULL**         |
| name          | varchar(100) | 이름             |                              |
| password_hash | varchar(255) | 비밀번호 해시     |                              |
| created_at    | timestamp    | 가입 시각        | `DEFAULT CURRENT_TIMESTAMP`  |

---

## 2. address (배송지)

| 컬럼명     | 타입         | 설명            | 비고                         |
|------------|--------------|-----------------|------------------------------|
| id         | bigint (PK)  | 배송지 ID       | AUTO_INCREMENT               |
| user_id    | bigint (FK)  | 소유 사용자     | `REFERENCES user(id)`        |
| receiver   | varchar(100) | 수령인          |                              |
| phone      | varchar(20)  | 연락처          |                              |
| zipcode    | varchar(10)  | 우편번호        |                              |
| memo       | varchar(255) | 배송 메모       |                              |
| is_default | boolean      | 기본 여부       | `DEFAULT false`              |

> 한 명의 사용자 → 여러 배송지 (1:N)

---

## 3. product (상품)

| 컬럼명     | 타입          | 설명          | 비고                         |
|------------|---------------|---------------|------------------------------|
| id         | bigint (PK)   | 상품 ID       | AUTO_INCREMENT               |
| name       | varchar(255)  | 상품명        |                              |
| description| text          | 상품 설명     |                              |
| price      | decimal(10,2) | 상품 가격     |                              |
| is_active  | boolean       | 판매 여부     | `DEFAULT true`               |
| created_at | timestamp     | 생성 시각     | `DEFAULT CURRENT_TIMESTAMP`  |

---

## 4. inventory (재고)

| 컬럼명     | 타입          | 설명                 | 비고                                                        |
|------------|---------------|----------------------|-------------------------------------------------------------|
| id         | bigint (PK)   | 재고 ID              | AUTO_INCREMENT                                              |
| product_id | bigint (FK)   | 대상 상품 ID         | **UNIQUE, NOT NULL**, `REFERENCES product(id)`              |
| stock      | int           | 실제 재고 수량       |                                                             |
| reserved   | int           | 예약(홀드) 수량      | `DEFAULT 0`                                                 |
| updated_at | timestamp     | 마지막 변경 시각      | `DEFAULT CURRENT_TIMESTAMP`                                 |

> 상품 1개당 재고 1로우를 강제하는 구조 → 옵션이 생기면 여기서 풀어주면 됨

---

## 5. cart_item (장바구니)

| 컬럼명     | 타입          | 설명             | 비고                                  |
|------------|---------------|------------------|---------------------------------------|
| id         | bigint (PK)   | 장바구니 ID      | AUTO_INCREMENT                        |
| user_id    | bigint (FK)   | 누구의 장바구니  | `REFERENCES user(id)`                 |
| product_id | bigint (FK)   | 어떤 상품인지    | `REFERENCES product(id)`              |
| qty        | int           | 수량             |                                       |
| created_at | timestamp     | 담은 시각        | `DEFAULT CURRENT_TIMESTAMP`           |
| (unique)   | -             | 중복 방지        | `UNIQUE (user_id, product_id)` 권장   |

---

## 6. "order" (주문)

> SQL 예약어라 따옴표로 감싸서 사용

| 컬럼명        | 타입          | 설명                         | 비고                                             |
|---------------|---------------|------------------------------|--------------------------------------------------|
| id            | bigint (PK)   | 주문 ID                      | AUTO_INCREMENT                                   |
| user_id       | bigint (FK)   | 주문한 사용자                | `REFERENCES user(id)`                            |
| address_id    | bigint (FK)   | 배송지                       | `REFERENCES address(id)`                         |
| status        | varchar(20)   | 주문 상태                    | `CREATED / RESERVED / PAID / CANCELLED`          |
| item_total    | decimal(10,2) | 상품 총액(할인 전)           |                                                  |
| coupon_discount | decimal(10,2) | 쿠폰 할인 금액             |                                                  |
| pay_amount    | decimal(10,2) | 실제 결제 금액               | `item_total - coupon_discount`                   |
| created_at    | timestamp     | 주문 시각                    | `DEFAULT CURRENT_TIMESTAMP`                      |

---

## 7. order_item (주문 상세)

| 컬럼명           | 타입          | 설명                      | 비고                                        |
|------------------|---------------|---------------------------|---------------------------------------------|
| id               | bigint (PK)   | 주문 상세 ID              | AUTO_INCREMENT                              |
| order_id         | bigint (FK)   | 어떤 주문인지             | `REFERENCES order(id)`                      |
| product_id       | bigint (FK)   | 어떤 상품이었는지         | `REFERENCES product(id)`                    |
| product_name_snap| varchar(255)  | 주문 당시 상품명          | 이후 상품명 변경돼도 이 값은 유지           |
| unit_price       | decimal(10,2) | 주문 당시 단가            |                                             |
| qty              | int           | 수량                      |                                             |

---

## 8. payment (결제)

| 컬럼명    | 타입          | 설명                  | 비고                                       |
|-----------|---------------|-----------------------|--------------------------------------------|
| id        | bigint (PK)   | 결제 ID               | AUTO_INCREMENT                             |
| order_id  | bigint (FK)   | 어떤 주문에 대한 결제 | `REFERENCES order(id)`                     |
| method    | varchar(20)   | 결제 수단             | 카드, 계좌 등                              |
| status    | varchar(20)   | 결제 상태             | `REQUESTED / SUCCEEDED / FAILED`           |
| amount    | decimal(10,2) | 결제 금액             |                                            |
| approved_at | timestamp   | 승인 시각             |                                            |

---

## 9. coupon (쿠폰 마스터)

| 컬럼명          | 타입          | 설명              | 비고                                 |
|-----------------|---------------|-------------------|--------------------------------------|
| id              | bigint (PK)   | 쿠폰 ID           | AUTO_INCREMENT                       |
| code            | varchar(50)   | 쿠폰 코드         | **UNIQUE, NOT NULL**                 |
| name            | varchar(100)  | 쿠폰 이름         |                                      |
| type            | varchar(10)   | 쿠폰 유형         | `PERCENT` \| `FIXED`                 |
| discount_value  | decimal(10,2) | 할인값            | **NOT NULL**                         |
| min_order_amount| decimal(10,2) | 최소 주문 금액    |                                      |
| start_at        | timestamp     | 사용 시작일       |                                      |
| end_at          | timestamp     | 사용 종료일       |                                      |
| is_active       | boolean       | 활성 여부         | `DEFAULT true`                       |
| created_at      | timestamp     | 생성 시각         | `DEFAULT CURRENT_TIMESTAMP`          |

---

## 10. user_coupon (사용자 보유 쿠폰)

| 컬럼명   | 타입          | 설명                             | 비고                                  |
|----------|---------------|----------------------------------|---------------------------------------|
| id       | bigint (PK)   | 사용자 쿠폰 ID                   | AUTO_INCREMENT                        |
| user_id  | bigint (FK)   | 누구에게 발급됐는지              | `REFERENCES user(id)`                 |
| coupon_id| bigint (FK)   | 어떤 쿠폰인지                    | `REFERENCES coupon(id)`               |
| status   | varchar(20)   | 상태                             | `ISSUED / USED / EXPIRED`             |
| issued_at| timestamp     | 발급 시각                        | `DEFAULT CURRENT_TIMESTAMP`           |
| used_at  | timestamp     | 사용 시각                        |                                       |
| order_id | bigint (FK)   | 어떤 주문에서 썼는지 추적        | `REFERENCES order(id)`                |

---
# 도메인 엔티티 설명
## 1. User 도메인
구성 테이블: user, address
역할: 인증 주체이자 주문의 주체. 하나의 유저가 여러 배송지를 가질 수 있게 분리. </br>
모델링 포인트: 배송지를 별도 테이블로 뺌으로써 추후 “최근 배송지”, “회사/집 구분” 같은 기능을 추가하기 쉽다.

## 2. 상품/재고 도메인
구성 테이블: product, inventory </br>
역할: 판매 가능한 상품에 대한 테이블과 실제 판매 가능 수량을 분리. </br>
모델링 포인트: 
inventory.reserved를 둬서 “주문이 들어와서 잠깐 잡아둔 상태”를 표현할 수 있다. </br>

## 3. 장바구니 도메인
구성 테이블: cart_item </br>
역할: 아직 주문으로 확정되지 않은 고객의 찜 목록. </br>
모델링 포인트: (user_id, product_id)를 UNIQUE로 두면 “같은 상품을 장바구니에 또 넣는 경우”를 수량 증가로 처리할 수 있다. </br>

## 4. 주문/결제 도메인
1.구성 테이블: "order", order_item, payment </br>
역할: 주문과 결제 결과를 분리해서 저장. </br>
모델링 포인트: 
주문 라인에 스냅샷을 저장해서 상품 정보 변경에 영향을 받지 않게 한다. </br>
상태 컬럼을 주문과 결제 모두에 두어서 비동기 PG 연동 시 커밋과 롤백을 유연하게 한다. </br>
역정규화를 통해 금액을 주문 테이블에 박아두면 나중에 회계/정산 쪽이 편해진다. </br>

## 5. 쿠폰 도메인
구성 테이블: coupon, user_coupon </br>
역할: 발행 가능한 쿠폰의 스펙과, 실제 유저에게 귀속된 쿠폰을 분리. </br>
모델링 포인트: 
user_coupon.order_id로 “이 쿠폰이 어느 주문에서 쓰였는지”를 추적할 수 있다. </br>
coupon.type이 PERCENT/FIXED로 나뉘어 있어서 서비스 로직에서 분기만 하면 된다. </br>

---

# 관계 요약

- `user` 1:N `address`
- `user` 1:N `cart_item`
- `user` 1:N `order`
- `user` 1:N `user_coupon`
- `product` 1:1 `inventory`
- `order` 1:N `order_item`
- `order` 1:1 `payment` (필요 시 1:N 확장 가능)
- `coupon` 1:N `user_coupon`

---

# 비즈니스 흐름 예시

1. 사용자가 상품을 장바구니에 담는다 → `cart_item`
2. 주문을 생성한다 → `"order"` + `order_item`
3. 쿠폰이 있으면 `user_coupon`에 사용 주문을 기록한다
4. 결제가 성공하면 `payment.status = SUCCEEDED`, 주문은 `PAID`로 변경
5. 재고는 `inventory`에서 실제 차감하고 `reserved`를 정리한다

---

# 📊 시스템 개요
| 구분        | 값/설명                                                                                                                    |
| --------- | ----------------------------------------------------------------------------------------------------------------------- |
| 전체 Entity | 10개 (`user`, `address`, `product`, `inventory`, `cart_item`, `order`, `order_item`, `payment`, `coupon`, `user_coupon`) |
| 주요 도메인    | 5개 (회원, 상품/재고, 장바구니, 주문/결제, 쿠폰)                                                                                         |
| 관계(주요)    | user→address(1:N), user→order(1:N), order→order_item(1:N), product→inventory(1:1), coupon→user_coupon(1:N)              |
| 핵심 제약     | 상품당 단일 재고, 장바구니 중복 방지, 쿠폰 코드 유니크, 주문-결제 1:1(기본형)                                                                        |
| 금액 정합성    | 주문 레벨에 합계/할인/실결제액을 모두 저장 → 추후 가격 변경에도 안전                                                                                |
| 동시성 포인트   | 재고 차감 시점(결제 성공 시), 쿠폰 사용 시점(ORDER에 묶을 때)                                                                                |
| 확장성 포인트   | `inventory`를 옵션 단위로 1:N 확장, `payment`를 1:N으로 바꾸면 재결제/부분환불 처리 가능                                                         |

---
# 📦 중요 제약 패턴 예시 (개념)
- 장바구니 중복 방지
  -   UNIQUE (user_id, product_id)
  - → 같은 상품을 같은 유저가 여러 줄로 넣는 상황 방지

- 상품-재고 1:1 보장
  - inventory.product_id UNIQUE
  - → 재고 로우가 두 개 생겨 재고가 분산되는 문제 방지

- 쿠폰 코드 유니크
  - coupon.code UNIQUE
  -  → 운영자가 배포한 코드가 중복되는 문제 방지
