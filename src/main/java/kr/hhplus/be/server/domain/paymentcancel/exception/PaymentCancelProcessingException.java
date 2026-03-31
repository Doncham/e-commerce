package kr.hhplus.be.server.domain.paymentcancel.exception;

public class PaymentCancelProcessingException extends PaymentCancelException{

	public PaymentCancelProcessingException(String reason) {
		super(PaymentCancelFailureType.ALREADY_PROCESSING, reason);
	}
}
