package kr.hhplus.be.server.application.payment;

import org.springframework.stereotype.Service;

import kr.hhplus.be.server.application.inventory.InventoryReservationService;
import kr.hhplus.be.server.application.point.PointReservationService;
import kr.hhplus.be.server.domain.inventoryReserve.InventoryReservationReleaseReason;
import kr.hhplus.be.server.domain.order.Order;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentReservationProcessor {
	private final PointReservationService pointHandler;
	private final InventoryReservationService inventoryHandler;

	public void confirm(Order order) {
		pointHandler.confirm(order);
		inventoryHandler.confirm(order);
	}

	public void release(Order order, InventoryReservationReleaseReason reason) {
		pointHandler.releaseAll(order, reason);
		inventoryHandler.releaseAll(order.getId(), reason);
	}
}
