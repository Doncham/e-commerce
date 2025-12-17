package kr.hhplus.be.server.domain.payment.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class AlreadyPaidException extends PaymentException{
	private static final String message = "이미 처리된 payment 입니다. paymentId = ";
	public AlreadyPaidException(ErrorCode code, Long paymentId) {
		super(code, message + paymentId);
	}
}
