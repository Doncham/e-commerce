package kr.hhplus.be.server.infrastructure.persistence.coupon;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import kr.hhplus.be.server.domain.coupon.Coupon;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Coupon> findByIdForUpdate(Long id);
}
