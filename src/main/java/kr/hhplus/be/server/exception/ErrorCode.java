package kr.hhplus.be.server.exception;

public enum ErrorCode {
	NEGATIVE_CHARGE, // 음수 포인트 충전 시도
	NOT_ENOUGH_POINT, // 포인트 부족

	NOT_FOUND_INVENTORY, // 인벤토리 없음
	NOT_SUFFICIENT_STOCK, // 재고 부족
	PAID_ORDER_MUST_HAVE_PAYMENT, // 결제된 주문에 주문 정보가 없음.
	PAY_AMOUNT_MISMATCH, // 결제 금액 불일치
	NOT_FOUND_ORDER, // 주문 없음
	POINT_AMOUNT_NOT_VALID,
	NOT_FOUND_COUPON, // 쿠폰 없음
	EXPIRED_COUPON, // 쿠폰 만료
	COUPON_FOR_USER_LIMIT_EXCEED, // 특정 유저의 쿠폰 발급 한도 초과
	COUPON_ISSUE_LIMIT_EXCEED, // 쿠폰 발급 한도 초과
}
