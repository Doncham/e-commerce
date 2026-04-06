package kr.hhplus.be.server.domain.payment;

public enum PaymentStatus {
	REQUESTED,
	SUCCESS,
	PARTIAL_CANCELED,
	CANCELLED,
	FAILURE
}
