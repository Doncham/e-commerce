package kr.hhplus.be.server.application.userCoupon;

import java.util.function.Supplier;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.redisson", name = "enabled", havingValue = "false", matchIfMissing = true)
public class NoOpCouponLockExecutor implements CouponLockExecutor {
	@Override
	public <T> T execute(Long couponId, Supplier<T> body) {
		return body.get();
	}
}