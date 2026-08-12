package kr.hhplus.be.server.domain.payment;

public enum PaymentStatus {
	READY,
	CONFIRMING,
	INVALIDATED,
	EXPIRED,
	SUCCESS,
	PARTIAL_CANCELED,
	CANCELLED,
	FAILED;
}
