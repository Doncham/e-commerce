package kr.hhplus.be.server.domain.paymentcancel.exception;

public class PaymentCancelPermanentException extends PaymentCancelException{
	public PaymentCancelPermanentException(String reason) {
		super(PaymentCancelFailureType.PERMANENT, reason);
	}
}
