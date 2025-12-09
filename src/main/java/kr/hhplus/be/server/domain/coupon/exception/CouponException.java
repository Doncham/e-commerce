package kr.hhplus.be.server.domain.coupon.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class CouponException extends RuntimeException{
	private final ErrorCode	code;

	public CouponException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}
}
