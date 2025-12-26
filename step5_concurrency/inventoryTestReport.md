# 재고 동시성 제어 보고서 

## 테스트 실행 방식
1.상품 1개 + 재고 100개를 준비한다.<br>
2.101개의 상품 주문 요청 DTO를 준비한다.(서로 다른 100명의 유저)<br>
3.CountDownLatch와 threadPool(16개 워커) 를 통해 16개의 워커들을 한번에 동작시켜서 짧은 시간에 101개의 요청을 처리하는 동시성 환경을 구성.<br>
``` java
for(int i = 0; i < requestCount; i++) {
    final int idx = i;
    Runnable task = () -> {
      // 메인 스레드 대기시키기
      ready.countDown();
      // 메인 스레드에 의해 활성화 될 때까지 대기
      await(start);
      try {
        // 주문 + 재고 예약
        orderCommandService.createOrder(requests.get(idx));
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
<br>

## 재고 예약 동시성 제어 실패 시 문제
저희 서비스에서는 주문할 때 재고 예약을 하고 결제할 때 재고 수량을 차감합니다. 즉, 재고 예약에서의 동시성 제어가 올바른 재고 수량 차감으로 이어집니다. <br>
위의 로직에서 createOrder 내부에서 동시성 제어를 하지 않으면 총 101개의 order가 생성되지만 실제로 예약된 재고를 확인해보면 10개 내외의 예약이 생깁니다.<br> 
그 이유는 락 없이 read-modify-write로 reserved를 갱신하면, 서로의 업데이트를 덮어쓴느 Lost Update가 발생하기 때문입니다. <br>
재고 예약이라는 취지에 맞지 않게 실제 재고보다 더 많은 주문이 생기고 **(주문-재고 불일치)** 이로 인해 정합성이 깨지는 결과를 초래합니다. 이로 인해 결제 단계에서 재고 부족이 뒤늦게 드러날 수 있어 운영 리스크가 증가합니다.

## 비관적 락으로 동시성 제어하기
``` java
@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "200"))
	@Query("""
		select i 
		from Inventory i
		where i.product.id in :productIds
		order by i.product.id asc
	""")
	List<Inventory> findByProductIdInForUpdate(@Param("productIds") List<Long> productsIds);
```
Lost Update는 임계 영역인 재고의 reserved 필드에 한번에 여러 스레드가 동시에 접근해서 발생하는 현상입니다. 이를 막기 위해 임계 영역에 접근할 때는 락을 통해 한번에 한 스레드만 접근시킵니다. <br>
낙관적 락 대신에 비관적 락을 사용한 이유는 인기 상품의 경우 여러 스레드가 접근하는 빈도가 높아지고 낙관적 락을 사용하는 경우 충돌 발생으로 인한 재시도가 지연과 DB 부하를 키울 수 있다고 생각했습니다. <br>
주문하기 로직에는 주문, 포인트, 쿠폰, 재고 등의 여러 도메인이 섞여 있기 때문에 잦은 재시도는 시스템의 성능에 영향을 끼칠 수 있다고 생각해서 낙관적 락 대신에 비관적 락을 선택했습니다.



