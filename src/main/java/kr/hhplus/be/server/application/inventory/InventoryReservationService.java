package kr.hhplus.be.server.application.inventory;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.domain.inventory.Inventory;
import kr.hhplus.be.server.domain.inventoryReserve.InventoryReservation;
import kr.hhplus.be.server.domain.inventoryReserve.InventoryReservationReleaseReason;
import kr.hhplus.be.server.domain.inventoryReserve.InventoryReserveStatus;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.infrastructure.persistence.inventory.InventoryRepository;
import kr.hhplus.be.server.infrastructure.persistence.inventoryReserve.InventoryReserveRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class InventoryReservationService {
	private final InventoryReserveRepository invReserveRepo;
	private final InventoryRepository invRepo;

	public void confirm(Order order) {
		List<InventoryReservation> reserves = invReserveRepo
			.findByOrderIdAndStatusForUpdate(order.getId(), InventoryReserveStatus.RESERVED);
		if (reserves.isEmpty()) return; // 멱등/재시도 안전

		List<Long> invIds = reserves.stream().map(InventoryReservation::getInventoryId).sorted().toList();
		List<Inventory> inventories = invRepo.findByIdsForUpdate(invIds);
		Map<Long, Inventory> map = inventories.stream().collect(Collectors.toMap(Inventory::getId, it -> it));

		for (InventoryReservation reservation : reserves) {
			Inventory inv = map.get(reservation.getInventoryId());
			if(reservation.confirm()) {
				inv.confirmReserve(reservation.getQty());
			}
		}
	}

	public void releaseAll(
		long orderId,
		InventoryReservationReleaseReason reason
	) {
		List<InventoryReservation> reserves = invReserveRepo
				.findByOrderIdAndStatusForUpdate(
					orderId,
					InventoryReserveStatus.RESERVED
				);

		if (reserves.isEmpty()) return; // 멱등/재시도 안전

		List<Long> invIds = reserves.stream()
			.map(InventoryReservation::getInventoryId)
			.distinct()
			.sorted()
			.toList();

		List<Inventory> inventories =
			invRepo.findByIdsForUpdate(invIds);

		if (inventories.size() != invIds.size()) {
			throw new IllegalStateException(
				"Some inventories do not exist. "
					+ "orderId=" + orderId
					+ ", requestedIds=" + invIds
					+ ", foundCount=" + inventories.size()
			);
		}

		Map<Long, Inventory> inventoryById =
			inventories.stream()
				.collect(Collectors.toMap(
					Inventory::getId,
					Function.identity()
				));


		// 실무에서는 InventoryReservation의 상태가 RESERVED에서 변경될 때만 실제 재고 차감
		for(InventoryReservation reservation : reserves) {
			Inventory inv = inventoryById.get(reservation.getInventoryId());

			if (inv == null) {
				throw new IllegalStateException(
					"Inventory does not exist. "
						+ "inventoryId="
						+ reservation.getInventoryId()
				);
			}

			if(reservation.release(reason)) {
				inv.releaseReserve(reservation.getQty());
			}
		}
	}
}
