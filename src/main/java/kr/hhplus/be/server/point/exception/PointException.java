package kr.hhplus.be.server.point.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class PointException extends RuntimeException{
	private final ErrorCode	 code;

	public PointException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}
}
