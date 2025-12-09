package kr.hhplus.be.server.domain.coupon.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class InsufficientCouponStockException extends CouponException {
	private static String message = "쿠폰 재고가 부족합니다.";
	public InsufficientCouponStockException(ErrorCode code, Long couponId) {
		super(code, message + " couponId=" + couponId);
	}

}
