package kr.hhplus.be.server.exception;

public enum ErrorCode {
	NEGATIVE_CHARGE, // 음수 포인트 충전 시도
	NOT_ENOUGH_POINT, // 포인트 부족

	NOT_FOUND_INVENTORY, // 인벤토리 없음
	NOT_SUFFICIENT_STOCK, // 재고 부족
	PAID_ORDER_MUST_HAVE_PAYMENT, // 결제된 주문에 주문 정보가 없음.
	PAY_AMOUNT_MISMATCH, // 결제 금액 불일치
	NOT_FOUND_ORDER, // 주문 없음
}
