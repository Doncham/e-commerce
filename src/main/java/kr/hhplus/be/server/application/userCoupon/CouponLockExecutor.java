package kr.hhplus.be.server.application.userCoupon;

import java.util.function.Supplier;

public interface CouponLockExecutor {
	<T> T execute(Long couponId, Supplier<T> body);
}
