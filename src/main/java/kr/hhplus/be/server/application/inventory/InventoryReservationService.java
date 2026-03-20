package kr.hhplus.be.server.application.inventory;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import kr.hhplus.be.server.domain.inventory.Inventory;
import kr.hhplus.be.server.domain.inventoryReserve.InventoryReservation;
import kr.hhplus.be.server.domain.inventoryReserve.InventoryReserveStatus;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.infrastructure.persistence.inventory.InventoryRepository;
import kr.hhplus.be.server.infrastructure.persistence.inventoryReserve.InventoryReserveRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InventoryReservationService {
	private final InventoryReserveRepository invReserveRepo;
	private final InventoryRepository invRepo;

	public void confirm(Order order) {
		List<InventoryReservation> reserves = invReserveRepo
			.findByOrderIdAndStatus(order.getId(), InventoryReserveStatus.RESERVED);
		if (reserves.isEmpty()) return; // 멱등/재시도 안전

		List<Long> invIds = reserves.stream().map(InventoryReservation::getInventoryId).sorted().toList();
		List<Inventory> inventories = invRepo.findByIdsForUpdate(invIds);
		Map<Long, Inventory> map = inventories.stream().collect(Collectors.toMap(Inventory::getId, it -> it));

		for (InventoryReservation r : reserves) {
			Inventory inv = map.get(r.getInventoryId());
			inv.confirmReserve(r.getQty());
			r.confirm();
		}
	}

	public void release(Order order, String reason) {
		List<InventoryReservation> reserves = invReserveRepo
			.findByOrderIdAndStatus(order.getId(), InventoryReserveStatus.RESERVED);
		if (reserves.isEmpty()) return; // 멱등/재시도 안전

		List<Long> invIds = reserves.stream().map(InventoryReservation::getInventoryId).sorted().toList();
		List<Inventory> inventories = invRepo.findByIdsForUpdate(invIds);
		Map<Long, Inventory> map = inventories.stream().collect(Collectors.toMap(Inventory::getId, it -> it));

		// 실무에서는 InventoryReservation의 상태가 RESERVED에서 변경될 때만 실제 재고 차감
		for(InventoryReservation r : reserves) {
			Inventory inv = map.get(r.getInventoryId());
			inv.releaseReserve(r.getQty());
			r.release("PAYMENT_FAILED");
		}
	}
}
