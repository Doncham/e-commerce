package kr.hhplus.be.server.domain.usercoupon.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class CouponIssueBusyException extends UserCouponException{
	private static final String message = "락을 획득하지 못했습니다. couponId = ";
	public CouponIssueBusyException(ErrorCode code, Long id) {
		super(code, message + id);
	}
}
