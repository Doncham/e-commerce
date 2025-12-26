# 쿠폰 발급 동시성 제어 보고서
## 개요
선착순 쿠폰 발급은 동일 쿠폰(row)에 트래픽이 집중되는 핫스팟 상황이 발생한다. <br>
락이 없으면 “재고 체크 → 발급 수 증가 → 발급 레코드 저장” 과정에서 동시 갱신 충돌이 발생해 초과 발급 또는 **카운트 불일치**(정합성 붕괴)가 생길 수 있다. <br>
본 과제에서는 **비관적 락**(Pessimistic Lock)을 적용하여 임계영역을 직렬화하고, “재고 100개면 정확히 100명만 성공”을 보장했다. <br>

## 테스트 실행 방식 
재고가 100개인 쿠폰 1개를 생성한다. <br>
서로 다른 200명의 유저를 생성하고, 동일 쿠폰 발급 요청 DTO를 200개 준비한다. <br>
CountDownLatch와 ThreadPool을 이용해 동시성 환경을 만들고, 짧은 시간에 요청을 집중시킨다. <br>
스레드풀 크기만큼의 작업이 동시에 실행되며, 나머지는 큐에서 대기 후 워커가 비면 순차 실행된다. <br>

## 동시성 실행 코드(핵심)
``` java
for (int i = 0; i < requestCount; i++) {
    final int idx = i;

    Runnable task = () -> {
      ready.countDown();   // “준비 완료”
      await(start);        // start 신호까지 대기
  
      try {
        userCouponService.createUserCoupon(requests.get(idx));
        success.incrementAndGet();
      } catch (Throwable e) {
        errors.add(e);
      } finally {
        done.countDown();
      }
  };

  pool.submit(task);
}

```

## 동시성 제어 실패 시 문제
쿠폰 발급 로직은 다음 흐름을 가진다.
- 쿠폰 조회
- 재고(hasStock) 확인
- 발급 수(issuedCount) 증가
- 사용자 쿠폰(UserCoupon) 저장

동시성 제어가 없다면 다음 문제가 발생할 수 있다.
- 여러 요청이 동시에 “재고가 있다”고 판단해 재고를 초과하여 발급(초과 발급)될 수 있다.
- 또는 issuedCount 업데이트가 서로 덮어써져 발급 레코드 수(UserCoupon)와 issuedCount가 불일치할 수 있다.
- 결과적으로 “재고 100개인데 100개 초과 발급” 또는 “발급은 됐는데 카운트가 덜 반영되는 정합성 붕괴”가 발생한다.

## 비관적 락으로 동시성 제어
쿠폰은 선착순 이벤트에서 특정 쿠폰(row)에 요청이 집중되는 핫스팟 데이터다. <br>
이 상황에서 낙관적 락(버전 충돌 기반)은 충돌 시 재시도가 필요하고, 충돌이 잦으면 지연시간과 DB 부하가 커질 수 있다. <br>

따라서 본 과제에서는 쿠폰 발급의 임계영역을 비관적 락으로 직렬화하여 <br>
“재고 체크 → 발급 수 증가 → 발급 레코드 저장”을 한 트랜잭션 내에서 안전하게 수행하도록 했다. 

## 락 적용 지점(예시: 쿠폰 조회)
``` java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "200"))
@Query("select c from Coupon c where c.id = :couponId")
Optional<Coupon> findByIdForUpdate(@Param("couponId") Long id);
```

## 재고 100개 / 유저 200명 동시 발급 테스트 검증 결과
기대: 성공 100 / 실패 100
검증:
- 쿠폰 issuedCount = 100
- 성공(success) = 100
- 실패(errors) = 100
- 실패 예외 타입 = InsufficientCouponStockException


