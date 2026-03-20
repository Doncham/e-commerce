package kr.hhplus.be.server.application.inventory;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.hhplus.be.server.domain.inventory.Inventory;
import kr.hhplus.be.server.domain.inventoryReserve.InventoryReservation;
import kr.hhplus.be.server.domain.inventoryReserve.InventoryReserveStatus;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.infrastructure.persistence.inventory.InventoryRepository;
import kr.hhplus.be.server.infrastructure.persistence.inventoryReserve.InventoryReserveRepository;

@ExtendWith(MockitoExtension.class)
class InventoryReservationServiceTest {

	@InjectMocks
	private InventoryReservationService handler;

	@Mock
	private InventoryReserveRepository invReserveRepo;
	@Mock private InventoryRepository invRepo;

	@Test
	void confirm_shouldConfirmInventoryReservations() {
		Long orderId = 1L;
		Order order = mock(Order.class);
		when(order.getId()).thenReturn(orderId);

		InventoryReservation r1 = InventoryReservation.reserve(orderId, 100L, 2L);

		when(invReserveRepo.findByOrderIdAndStatus(orderId, InventoryReserveStatus.RESERVED))
			.thenReturn(List.of(r1));

		Inventory inv = mock(Inventory.class);
		when(inv.getId()).thenReturn(100L);

		when(invRepo.findByIdsForUpdate(List.of(100L))).thenReturn(List.of(inv));

		handler.confirm(order);

		verify(inv).confirmReserve(2L);
		assertEquals(InventoryReserveStatus.CONFIRMED, r1.getStatus());
	}

	@Test
	void release_shouldReleaseInventoryReservations() {
		Long orderId = 1L;
		Order order = mock(Order.class);
		when(order.getId()).thenReturn(orderId);

		InventoryReservation r1 = InventoryReservation.reserve(orderId, 100L, 2L);

		when(invReserveRepo.findByOrderIdAndStatus(orderId, InventoryReserveStatus.RESERVED))
			.thenReturn(List.of(r1));

		Inventory inv = mock(Inventory.class);
		when(inv.getId()).thenReturn(100L);

		when(invRepo.findByIdsForUpdate(List.of(100L))).thenReturn(List.of(inv));

		handler.release(order, "PAYMENT_FAILED");

		verify(inv).releaseReserve(2L);
		assertEquals(InventoryReserveStatus.RELEASED, r1.getStatus());
	}
}