package kr.hhplus.be.server.infrastructure.persistence.inventory;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import kr.hhplus.be.server.domain.inventory.Inventory;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
	Optional<Inventory> findByProductId(Long productId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
	@Query("""
		select i 
		from Inventory i
		where i.product.id in :productIds
		order by i.product.id asc
	""")
	List<Inventory> findByProductIdInForUpdate(@Param("productIds") List<Long> productsIds);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select i
		from Inventory i
		where i.id in (:invIds)
		order by i.id
	""")
	List<Inventory> findByIdsForUpdate(@Param("invIds") List<Long> invIds);
}
