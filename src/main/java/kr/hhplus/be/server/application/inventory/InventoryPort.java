package kr.hhplus.be.server.application.inventory;

import java.util.List;

import kr.hhplus.be.server.domain.inventory.Inventory;

public interface InventoryPort {
	List<Inventory> findByProductIdInForUpdateOrderByProductId(List<Long> productIds);
	List<Inventory> findByProductIdInOrderByProductId(List<Long> productIds);
}
