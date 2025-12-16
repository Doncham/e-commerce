package kr.hhplus.be.server.domain.point.exception;

import kr.hhplus.be.server.domain.pointcharge.exception.PointChargeException;
import kr.hhplus.be.server.exception.ErrorCode;

public class PointChargeNegativeException extends PointChargeException {
	private static String message = "포인트 충전 금액이 음수입니다. userId = ";
	public PointChargeNegativeException(ErrorCode code, Long userId, String idempotencyKey) {
		super(code, message + userId +" idempotencyKey = " + idempotencyKey);
	}
}
