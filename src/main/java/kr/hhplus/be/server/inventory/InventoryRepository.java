package kr.hhplus.be.server.inventory;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
	Optional<Inventory> findByProductId(Long productId);

	List<Inventory> findByProductIdIn(List<Long> productsIds);
}
