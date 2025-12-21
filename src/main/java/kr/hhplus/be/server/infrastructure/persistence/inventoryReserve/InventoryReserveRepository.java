package kr.hhplus.be.server.infrastructure.persistence.inventoryReserve;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.domain.inventoryReserve.InventoryReservation;
import kr.hhplus.be.server.domain.inventoryReserve.InventoryReserveStatus;

@Repository
public interface InventoryReserveRepository extends JpaRepository<InventoryReservation, Long> {
	List<InventoryReservation> findByOrderIdAndStatus(Long id, InventoryReserveStatus inventoryReserveStatus);
}
