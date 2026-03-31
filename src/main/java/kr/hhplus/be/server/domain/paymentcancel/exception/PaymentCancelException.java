package kr.hhplus.be.server.domain.paymentcancel.exception;

public class PaymentCancelException extends RuntimeException{
	private final PaymentCancelFailureType failureType;
	private final String reason;

	public PaymentCancelException(PaymentCancelFailureType failureType, String reason) {
		this.failureType = failureType;
		this.reason = reason;
	}
}
