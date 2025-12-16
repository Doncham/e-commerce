package kr.hhplus.be.server.infrastructure.persistence.inventoryReserve;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.domain.inventoryReserve.InventoryReservation;

@Repository
public interface InventoryReserveRepository extends JpaRepository<InventoryReservation, Long> {
}
