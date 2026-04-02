package kr.hhplus.be.server.domain.paymentcancel;

public enum PaymentCancelStatus {
	REQUESTED,
	PROCESSING,
	SUCCEEDED,
	FAILED_RETRYABLE,
	FAILED_PERMANENT
}
