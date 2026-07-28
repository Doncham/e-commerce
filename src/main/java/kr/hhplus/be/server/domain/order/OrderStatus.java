package kr.hhplus.be.server.domain.order;

public enum OrderStatus {
	DRAFT,
	PAYMENT_PENDING,
	CANCELED,
	PARTIAL_CANCELED,
	FAILED,
	PAID;
}
