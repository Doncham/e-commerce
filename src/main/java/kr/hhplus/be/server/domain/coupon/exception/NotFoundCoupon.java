package kr.hhplus.be.server.domain.coupon.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class NotFoundCoupon extends CouponException {
	private static String message = "쿠폰을 찾을 수 없습니다.";
	public NotFoundCoupon(ErrorCode code, Long couponId) {
		super(code, message + " couponId=" + couponId);
	}
}
