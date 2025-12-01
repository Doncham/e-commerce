package kr.hhplus.be.server.inventory.port.out;

import java.util.List;

import kr.hhplus.be.server.inventory.Inventory;

public interface InventoryPort {
	List<Inventory> findByProductIdIn(List<Long> productIds);
}
