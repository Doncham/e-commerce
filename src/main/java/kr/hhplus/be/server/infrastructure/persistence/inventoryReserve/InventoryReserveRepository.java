package kr.hhplus.be.server.infrastructure.persistence.inventoryReserve;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import kr.hhplus.be.server.domain.inventoryReserve.InventoryReservation;
import kr.hhplus.be.server.domain.inventoryReserve.InventoryReserveStatus;

@Repository
public interface InventoryReserveRepository extends JpaRepository<InventoryReservation, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query(""" 
   			select ir
			from InventoryReservation ir
			where ir.orderId = :orderId 
				and ir.status = :status
			order by ir.inventoryId asc
""")
	List<InventoryReservation> findByOrderIdAndStatusForUpdate(@Param("orderId") Long orderId, @Param("status") InventoryReserveStatus inventoryReserveStatus);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query(""" 
   			select ir
			from InventoryReservation ir
			where ir.orderId = :orderId 
			order by ir.inventoryId asc
""")
	List<InventoryReservation> findByOrderIdForUpdate(Long id);
}
