package kr.hhplus.be.server.infrastructure.persistence.pointReservation;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import kr.hhplus.be.server.domain.pointReservation.PointReservation;

@Repository
public interface PointReservationRepository extends JpaRepository<PointReservation, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select pr
		from PointReservation pr
		where pr.orderId = :orderId
""")
	Optional<PointReservation> findByOrderIdForUpdate(@Param("orderId") Long orderId);

}
