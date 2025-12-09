package kr.hhplus.be.server.infrastructure.persistence.userCoupon;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.domain.usercoupon.UserCoupon;

@Repository
public interface UserCouponRepository extends JpaRepository<UserCoupon, Long> {
	// 수량 조회
	Long countAllByUserIdAndCouponId(Long userId, Long couponId);
}
