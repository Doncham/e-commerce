package kr.hhplus.be.server.domain.order;

public enum OrderStatus {
	DRAFT,
	CREATED,
	PAYMENT_PENDING,
	PAYMENT_COMPLETE,
	CANCELED,
	PARTIAL_CANCELED,
	FAILED,
	PAID;
}
