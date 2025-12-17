package kr.hhplus.be.server.domain.payment.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class PaymentNotFoundException extends PaymentException{
	private static final String message = "payment를 찾을 수 없습니다. paymentId = ";
	public PaymentNotFoundException(ErrorCode code, Long paymentId) {
		super(code, message + paymentId);
	}
}
