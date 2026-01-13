package kr.hhplus.be.server.domain.usercoupon.exception;

import kr.hhplus.be.server.exception.ErrorCode;
import lombok.Getter;

@Getter
public class UserCouponException extends RuntimeException {
	private final ErrorCode code;

	public UserCouponException(ErrorCode code,String message) {
		super(message);
		this.code = code;
	}
}
