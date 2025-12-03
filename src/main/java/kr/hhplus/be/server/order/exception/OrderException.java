package kr.hhplus.be.server.order.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class OrderException extends RuntimeException{
	private final ErrorCode code;

	public OrderException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}
}
