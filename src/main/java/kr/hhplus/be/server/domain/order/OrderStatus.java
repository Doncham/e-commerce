package kr.hhplus.be.server.domain.order;

public enum OrderStatus {
	DRAFT,
	CREATED,
	CANCELED,
	PARTIAL_CANCELED,
	FAILED,
	PAID;
}
