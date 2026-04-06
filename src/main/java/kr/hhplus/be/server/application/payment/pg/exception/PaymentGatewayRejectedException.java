package kr.hhplus.be.server.application.payment.pg.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class PaymentGatewayRejectedException extends PGException{
	public PaymentGatewayRejectedException(String message) {
		super(ErrorCode.PAYMENT_GATEWAY_REJECTED, message);
	}
}
