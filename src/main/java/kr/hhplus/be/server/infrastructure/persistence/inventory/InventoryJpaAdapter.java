package kr.hhplus.be.server.infrastructure.persistence.inventory;

import java.util.List;

import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.domain.inventory.Inventory;
import kr.hhplus.be.server.application.inventory.InventoryPort;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class InventoryJpaAdapter implements InventoryPort {
	private final InventoryRepository inventoryRepository;

	@Override
	public List<Inventory> findByProductIdInForUpdateOrderByProductId(List<Long> productIds) {
		return inventoryRepository.findByProductIdInForUpdate(productIds);
	}
}
