package kr.hhplus.be.server.domain.payment;

public enum PaymentStatus {
	READY,
	CONFIRMING,
	RESET,
	EXPIRED,
	SUCCESS,
	PARTIAL_CANCELED,
	CANCELLED,
	FAILED;
}
