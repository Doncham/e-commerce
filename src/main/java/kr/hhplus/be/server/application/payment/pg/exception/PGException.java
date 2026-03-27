package kr.hhplus.be.server.application.payment.pg.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class PGException extends RuntimeException{
	private final ErrorCode code;

	public PGException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}
}
