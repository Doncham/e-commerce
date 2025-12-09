package kr.hhplus.be.server.domain.coupon.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class UserCouponLimitExceededException extends CouponException {
	private static String message = "해당 유저의 쿠폰 발급 한도를 초과했습니다.";
	public UserCouponLimitExceededException(ErrorCode code, Long couponId, Long userId) {
		super(code, message + " couponId=" + couponId +" userId=" + userId);
	}
}
