# Payment Failure Scenarios

## 1. PG confirm timeout

### Given
- Order = PAYMENT_PENDING
- Payment = READY

### When
1. Payment READY -> CONFIRMING commit
2. PG confirm 요청
3. 서버가 timeout 발생

### Expected
- Payment는 CONFIRMING 유지
- Order는 PAYMENT_PENDING 유지
- 재고/포인트 예약 유지
- 새로운 결제 시작 금지
- reconciliation scheduler가 PG 상태 조회
