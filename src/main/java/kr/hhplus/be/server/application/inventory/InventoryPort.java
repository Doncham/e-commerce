package kr.hhplus.be.server.application.inventory;

import java.util.List;

import kr.hhplus.be.server.domain.inventory.Inventory;

public interface InventoryPort {
	List<Inventory> findByProductIdIn(List<Long> productIds);
}
