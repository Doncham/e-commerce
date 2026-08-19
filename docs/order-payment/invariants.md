# Order / Payment Invariants

- Order와 Payment는 1:1이다.
- Payment가 CONFIRMING이면 Order를 수정할 수 없다.
- Payment가 CONFIRMING이면 새로운 payment prepare를 할 수 없다.
- CONFIRMING은 timeout만으로 FAILED 처리하지 않는다.
- 한 사용자가 동시에 여러 Payment를 CONFIRMING 상태로 만들 수 없다.
- Order가 PAID라면 Payment는 SUCCESS여야 한다.
- Payment SUCCESS 처리 시 재고/포인트 예약도 CONFIRMED로 수렴해야 한다.
