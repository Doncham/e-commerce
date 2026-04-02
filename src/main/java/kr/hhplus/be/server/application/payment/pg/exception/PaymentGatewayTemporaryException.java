package kr.hhplus.be.server.application.payment.pg.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class PaymentGatewayTemporaryException extends PGException{
	public PaymentGatewayTemporaryException(String message) {
		super(ErrorCode.PAYMENT_GATEWAY_TEMPORARILY_STOP, message);
	}
}
