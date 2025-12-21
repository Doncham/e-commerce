package kr.hhplus.be.server.infrastructure.persistence.pointReservation;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.domain.pointReservation.PointReservation;

@Repository
public interface PointReservationRepository extends JpaRepository<PointReservation, Long> {
	Optional<PointReservation> findByOrderId(Long orderId);
}
