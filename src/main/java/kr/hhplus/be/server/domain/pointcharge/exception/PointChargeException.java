package kr.hhplus.be.server.domain.pointcharge.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class PointChargeException extends RuntimeException {
	private final ErrorCode code;
	public PointChargeException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}
}
