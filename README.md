# 🛍️ HangHaePlus Server — E-Commerce Backend

## 프로젝트 개요
HangHaePlus는 Spring Boot 기반의 전자상거래 백엔드 서버로, 상품 조회·장바구니·주문·결제·쿠폰 관리까지의 전 과정을 다루는 시스템입니다.

## 🗂️ 문서
- [API 명세서](https://github.com/Doncham/e-commerce/blob/readme/docs/openapi.yaml)
- [ERD](https://github.com/Doncham/e-commerce/blob/readme/docs/erd.md)
- [인프라 구성도](https://github.com/Doncham/e-commerce/blob/readme/docs/infra.md)

---

## 주요 시나리오
1️⃣ 사용자는 상품을 조회하고 장바구니에 담는다.
2️⃣ 장바구니를 기반으로 주문 초안을 생성한다.
3️⃣ 쿠폰을 적용하거나 해제한다.
4️⃣ 결제 요청 시 Idempotency-Key 를 통해 중복 결제를 방지한다.
5️⃣ 결제 서버의 웹훅을 통해 최종 결제 성공 여부를 수신한다.
6️⃣ 결제가 승인되면 재고와 쿠폰이 커밋되어 주문이 확정된다.

---

## ⚙ 기술 스택
| 구분        | 사용 기술                                  |
| --------- | -------------------------------------- |
| Language  | Java 17                                |
| Framework | Spring Boot 3, Spring Security, JPA    |
| DB        | MySQL (RDS)                            |
| Cache     | Redis (재고/쿠폰 예약)                       |
| MQ        | RabbitMQ (비동기 결제/쿠폰 이벤트)               |
| Infra     | AWS EC2, Nginx, Docker, GitHub Actions |
| Docs      | OpenAPI 3.0, Mermaid ERD               |

---

## 🚀 API 요약
| 기능             | 경로                                      | 설명            |
| -------------- | --------------------------------------- | ------------- |
| 회원가입           | `POST /api/v1/auth/signup`              | 사용자 등록        |
| 로그인            | `POST /api/v1/auth/login`               | JWT 발급        |
| 상품목록 조회        | `GET /api/v1/products`                  | 검색/정렬/필터      |
| 장바구니 관리        | `POST /api/v1/cart/items`               | 장바구니 추가/삭제    |
| 주문초안 생성        | `POST /api/v1/orders/draft`             | 장바구니 기반 주문 생성 |
| 쿠폰 적용/해제       | `POST /api/v1/orders/{id}/apply-coupon` | 할인 적용         |
| 결제 인텐트 생성      | `POST /api/v1/orders/{id}/checkout`     | 결제 세션 생성      |
| 결제 결과(Webhook) | `POST /internal/payments/webhook`       | PG 서버 콜백      |

---
## 🧪 테스트 전략
단위 테스트 : JUnit + Mockito 로 도메인 단위 검증
통합 테스트 : Testcontainers 기반으로 MySQL, Redis 실환경 테스트
시나리오 테스트 : 주문→결제→웹훅→재고 커밋까지 end-to-end 검증

---
## ✅ 핵심 설계 포인트
| 항목     | 설명                             |
| ------ | ------------------------------ |
| 동시성 제어 | 재고·쿠폰 예약 시 Redisson Lock 사용    |
| 멱등성 보장 | 결제/주문 요청에 Idempotency-Key 적용   |
| 확장성    | 도메인 모듈 단위 배포 가능 구조             |
| 일관성    | 결제 완료 시점에 재고, 쿠폰, 주문 상태 동기화    |
| 장애 복원성 | RabbitMQ 기반 비동기 Retry 및 DLQ 적용 |


