package kr.hhplus.be.server.domain.coupon.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class CouponExpiredException extends CouponException {
	private static String message = "쿠폰이 만료되었습니다.";
	public CouponExpiredException(ErrorCode code, Long couponId) {
		super(code, message + " couponId=" + couponId);
	}
}
