package kr.hhplus.be.server.domain.inventoryReserve;

public enum InventoryReservationReleaseReason {
	ORDER_CHANGED,
	PAYMENT_EXPIRED,
	PAYMENT_FAILED,
	PAYMENT_CANCELED,
	ORDER_CANCELED,
	ADMIN_CANCELED
}