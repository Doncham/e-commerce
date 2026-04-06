package kr.hhplus.be.server.application.inventory;

import java.util.List;

import org.springframework.stereotype.Service;

import kr.hhplus.be.server.domain.inventory.Inventory;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.infrastructure.persistence.inventory.InventoryRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class InventoryService {
	private final InventoryRepository inventoryRepo;

	public void restoreInventory(List<OrderProduct> orderProducts) {
		List<Long> productIds = orderProducts.stream()
			.map(OrderProduct::getProductId)
			.toList();
		List<Inventory> inventories = inventoryRepo.findByIdsForUpdate(productIds);
		inventories.forEach(Inventory::restoreOne);
	}
}
