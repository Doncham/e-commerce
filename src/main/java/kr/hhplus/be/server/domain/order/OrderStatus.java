package kr.hhplus.be.server.domain.order;

public enum OrderStatus {
	DRAFT,
	CREATED,
	CANCELLED,
	PARTIAL_CANCELED,
	FAILED,
	PAID;
}
