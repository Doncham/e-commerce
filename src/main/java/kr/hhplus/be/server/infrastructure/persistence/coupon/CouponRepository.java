package kr.hhplus.be.server.infrastructure.persistence.coupon;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import kr.hhplus.be.server.domain.coupon.Coupon;

@Repository
public interface CouponRepository extends JpaRepository<Coupon, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "200"))
	@Query("select c from Coupon c where c.id = :couponId")
	Optional<Coupon> findByIdForUpdate(@Param("couponId") Long id);
}
