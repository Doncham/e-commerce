package kr.hhplus.be.server.application.payment.pg.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class PaymentCancelFailException extends PGException{
	private static final String message = "PG 서버의 일시적인 장애 발생 paymentId: ";

	public PaymentCancelFailException(ErrorCode code, Long paymentId) {
		super(code, this.message + paymentId);
	}
}
