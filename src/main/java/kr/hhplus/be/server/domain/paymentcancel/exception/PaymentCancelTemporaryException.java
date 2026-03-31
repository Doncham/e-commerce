package kr.hhplus.be.server.domain.paymentcancel.exception;

public class PaymentCancelTemporaryException extends PaymentCancelException{
	public PaymentCancelTemporaryException(String reason) {
		super(PaymentCancelFailureType.TEMPORARY, reason);
	}
}
