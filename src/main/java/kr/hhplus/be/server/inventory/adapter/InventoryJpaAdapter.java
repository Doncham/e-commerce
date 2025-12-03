package kr.hhplus.be.server.inventory.adapter;

import java.util.List;

import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.inventory.Inventory;
import kr.hhplus.be.server.inventory.InventoryRepository;
import kr.hhplus.be.server.inventory.port.out.InventoryPort;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class InventoryJpaAdapter implements InventoryPort {
	private final InventoryRepository inventoryRepository;

	@Override
	public List<Inventory> findByProductIdIn(List<Long> productIds) {
		return inventoryRepository.findByProductIdIn(productIds);
	}
}
