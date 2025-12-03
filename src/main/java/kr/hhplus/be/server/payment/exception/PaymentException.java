package kr.hhplus.be.server.payment.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class PaymentException extends RuntimeException {
	private final ErrorCode code;

	public PaymentException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}
}
