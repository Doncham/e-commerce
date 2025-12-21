package kr.hhplus.be.server.infrastructure.persistence.pointcharge;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.domain.pointcharge.PointCharge;
@Repository
public interface PointChargeRepository extends JpaRepository<PointCharge, Long> {
	Optional<PointCharge> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}
