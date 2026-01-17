package kr.hhplus.be.server.application.userCoupon;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import kr.hhplus.be.server.domain.usercoupon.exception.CouponIssueBusyException;
import kr.hhplus.be.server.exception.ErrorCode;
import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnProperty(prefix = "app.redisson", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class RedissonCouponLockExecutor implements CouponLockExecutor {
	private final RedissonClient redissonClient;

	@Override
	public <T> T execute(Long couponId, Supplier<T> body) {
		RLock lock = redissonClient.getLock("lock:coupon:" + couponId);
		boolean locked = false;
		try {
			locked = lock.tryLock(200, 7, TimeUnit.SECONDS); // 예시: strict fail-fast
			if (!locked) throw new CouponIssueBusyException(ErrorCode.COUPON_ISSUE_BUSY, couponId);
			return body.get();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("락 획득 대기 중 인터럽트", e);
		} finally {
			if (locked && lock.isHeldByCurrentThread()) lock.unlock();
		}
	}
}