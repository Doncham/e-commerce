# Redis 캐시 적용을 통한 인기 상품 조회 성능 개선 보고서

- 대상 시나리오: **인기 상품 조회 API** (`GET /api/v1/product/popular?range=SEVEN|THIRTY`)
- 목적: 조회 지연이 큰 쿼리를 분석하고, **Redis 기반 캐시 전략**을 적용하여 **성능 개선을 정량적으로 검증**한다.
- 측정 도구: **k6**
- 캐시 저장소: **Redis** (멀티 인스턴스 환경 가정)
- 캐시 일관성 정책: **TTL 기반** + 결제 완료 시 캐시 무효화(invalidate) 가능

---

## 1. 배경 및 문제 정의

### 1.1 문제
인기 상품 조회는 “기간 내 결제 완료(PAID) 주문”의 주문상품(order_product) 데이터를 집계하여 **판매량 기준 Top N**을 산출한다.  
특성상 다음이 발생한다.

- **Join + Where + Group By + Order By + Limit** 조합 → DB 부하 큼
- 트래픽이 커질수록 p95/p99가 급격히 상승
- 동일한 요청(예: SEVEN, THIRTY)이 반복 호출되는 비율이 높음 → 캐시 적합

### 1.2 캐시 후보 구간 선정
- 인기 상품 결과는 **자주 조회되지만, 매 요청마다 정확히 최신일 필요는 낮은 데이터**로 판단
- TTL(예: 2분~5분) 정도의 정합성 지연을 허용 가능
- 멀티 인스턴스 환경 고려 시, 로컬 메모리 캐시보다 **Redis 캐시가 적합**

---

## 2. 조회 쿼리 이해 및 병목 분석

### 2.1 집계 쿼리(JPQL)
```java
@Query("""
    select new kr.hhplus.be.server.application.product.ProductSoldQtyDTO(op.productId, SUM(op.qty))
    from OrderProduct as op
    join op.order o
    join Product p on p.id = op.productId
    where o.createdAt >= :from
      and o.createdAt < :to
      and p.deletedAt is null
      and p.isActive = true
      and o.status = :status
    group by op.productId
    order by sum(op.qty) desc
""")
List<ProductSoldQtyDTO> findPopularProduct(
    @Param("from") LocalDateTime from,
    @Param("to") LocalDateTime to,
    @Param("status") OrderStatus status,
    Pageable pageRequest
);
```

### 2.2 DB 관점 동작 흐름
1. orders에서 created_at 범위 + status=PAID 조건으로 후보 주문을 좁힘
2. 후보 주문의 order_id를 이용해 order_product를 찾아 qty를 집계
3. product를 조인하여 활성/삭제 조건 필터링
4. group by product_id로 판매량 합계 산출
5. order by sum(qty) desc 후 limit N

### 2.3 DB 세팅(dummy data)
1. product 40,000
2. order 50,000
3. order_product 130,000

난수를 활용하여 균등하게 product를 선택함. 

#### 병목 포인트
기간 내 주문이 많을수록 order_product 스캔/집계 비용이 증가 <br>
group by + order by aggregate는 인덱스만으로 해결이 어렵고, 대량 데이터에서 비용이 커짐 <br>
결과를 만들기 위해 매번 동일한 계산을 반복한다는 점이 가장 큰 낭비 <br>

## 3.인덱스
### 3.1 orders
orders(created_at) : 기간 필터에 핵심 <br>
order_product와 product는 기본키로 생성된 클러스터드 인덱스 사용.

## 4. 캐시 전략 설계
### 4.1 선택한 전략: “인기 상품 리스트 캐시 + TTL”
- 캐시 키: popular:{range} (popular:SEVEN, popular:THIRTY)
- 캐시 값: PopularProductsResponse JSON 직렬화 결과
- TTL: 120초(현재 구현)
  - 과제/테스트에서는 TTL을 짧게 두고 갱신을 자주 보도록 구성
  - 운영에서는 5분 등으로 조정 가능

### 4.2 캐시 스탬피드 방지(멀티 인스턴스 고려)
캐시 만료 순간 트래픽이 몰리면, 여러 인스턴스가 동시에 DB를 때리는 문제가 생길 수 있다.
이를 방지하기 위해 Redisson 분산 락을 추가했다.
- 락 키: lock:popular:{range}
- 캐시 미스 시:
  1. 락 시도
  2. 락 획득 후 double-check(재확인) → 다른 인스턴스가 먼저 채웠는지 확인
  3. DB 조회 → 캐시 set → 반환
  4. 락 실패 시 짧게 재시도 후 캐시 재조회, 그래도 없으면 DB fallback

## 5. 성능 테스트(k6) 설계
### 5.1 테스트 목적
- 캐시 적용 전: 병목이 되는 조회 쿼리의 지연을 확인
- 캐시 적용 후: 동일 부하에서 지연이 감소하고 처리량이 증가하는지 확인

### 5.2 테스트 조건(동일하게 유지)
- VU: 최대 100
- stages: 30s ramp-up → 1m steady → 30s ramp-down
- threshold:
  - p(95) < 300ms
  - p(99) < 600ms
  - 실패율 < 1%
## 6. k6 결과(캐시 적용 전/후)
### 6.1 캐시 적용 전 결과(요약)
- 처리량: 24.80 RPS
- p95: 5.23s
- p99: 6.07s
- 실패율: 0%
- 결론: 목표 SLO(p95<300ms, p99<600ms) 미달

“죽지는 않지만 매우 느림” 상태로, 대량 트래픽에서 지연이 급격히 증가하는 병목이 확인됨.

### 6.2 캐시 적용 후 결과(요약)
- 처리량: 1116.83 RPS
- avg: 66.71ms
- p95: 116.53ms
- p99: 166.91ms
- 실패율: 0%

결론: 목표 SLO 충족

### 6.3 개선 효과(정량 비교)
- RPS: 24.8 → 1116.8 (약 45배 증가)
- p95: 5.23s → 116ms (약 45배 감소)
- p99: 6.07s → 167ms (수십 배 감소)
- 실패율: 동일하게 0%

**해석**
- 캐시 적용 전에는 VU가 대부분 DB 응답을 기다리며 블로킹됨 → 처리량이 낮고 지연이 큼
- 캐시 적용 후에는 Redis 응답이 빠르게 반환되어 VU가 빠르게 반복 호출 → 처리량이 크게 증가
