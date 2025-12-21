package kr.hhplus.be.server.domain.pointcharge.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class PointChargeNotFoundException extends PointChargeException{
	private static String message = "pointCharge doesn't exist userId= ";
	public PointChargeNotFoundException(ErrorCode code, Long userId, String idempotencyKey) {
		super(code, message + userId +"idempotencyKey= " + idempotencyKey);
	}
}
