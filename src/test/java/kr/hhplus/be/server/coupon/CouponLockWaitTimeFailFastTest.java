package kr.hhplus.be.server.coupon;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import kr.hhplus.be.server.FixturePersist;
import kr.hhplus.be.server.TestFixture;
import kr.hhplus.be.server.api.usercoupon.request.UserCouponCreateRequest;
import kr.hhplus.be.server.application.userCoupon.CouponLockExecutor;
import kr.hhplus.be.server.application.userCoupon.UserCouponFacade;
import kr.hhplus.be.server.domain.coupon.Coupon;
import kr.hhplus.be.server.domain.user.User;
import kr.hhplus.be.server.domain.usercoupon.exception.CouponIssueBusyException;
import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.infrastructure.persistence.coupon.CouponRepository;
import kr.hhplus.be.server.infrastructure.persistence.user.UserRepository;

@SpringBootTest
public class CouponLockWaitTimeFailFastTest {

	@Autowired UserRepository userRepo;
	@Autowired CouponRepository couponRepo;
	@Autowired FixturePersist fixturePersist;

	@Autowired UserCouponFacade userCouponFacade;
	static CountDownLatch firstLockAcquired;

	@Test
	void waitTimeTooShort_shouldFailFastForSecondCaller() throws Exception {
		// given
		Coupon coupon = fixturePersist.saveAndFlush(couponRepo, TestFixture.percent10CouponForOneUser(100L));
		User u1 = fixturePersist.saveAndFlush(userRepo, TestFixture.user());
		User u2 = fixturePersist.saveAndFlush(userRepo, TestFixture.user());

		UserCouponCreateRequest r1 = UserCouponCreateRequest.of(u1.getId(), coupon.getId());
		UserCouponCreateRequest r2 = UserCouponCreateRequest.of(u2.getId(), coupon.getId());

		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		firstLockAcquired = new CountDownLatch(1);

		Future<?> f1 = pool.submit(() -> {
			await(start);
			userCouponFacade.createUserCoupons(r1); // 첫 스레드: 락 잡고 hold-ms 동안 지연
		});

		Future<?> f2 = pool.submit(() -> {
			await(start);
			await(firstLockAcquired);
			userCouponFacade.createUserCoupons(r2); // 두 번째: waitTime 100ms라서 Busy 기대
		});

		// when
		start.countDown();

		// then
		f1.get(5, TimeUnit.SECONDS); // 첫 번째는 성공해야 함

		ExecutionException ex = assertThrows(ExecutionException.class, () -> f2.get(5, TimeUnit.SECONDS));
		Throwable root = rootCause(ex);

		assertTrue(root instanceof CouponIssueBusyException,
			"waitTime이 hold 시간보다 짧으면 두 번째 호출은 Busy로 실패해야 한다. root=" + root);
		pool.shutdownNow();
	}

	private static void await(CountDownLatch latch) {
		try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
	}

	private static Throwable rootCause(Throwable t) {
		Throwable cur = t;
		while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
		return cur;
	}

	@TestConfiguration
	static class WaitTimeTestLockConfig {
		@Bean
		@Primary
		CouponLockExecutor tunableRedissonLockExecutor(
			RedissonClient redissonClient,
			Environment env
		) {
			long waitMs  = Long.parseLong(env.getProperty("test.lock.wait-ms", "100"));
			long leaseMs = Long.parseLong(env.getProperty("test.lock.lease-ms", "3000"));
			long holdMs  = Long.parseLong(env.getProperty("test.lock.hold-ms", "200"));
			return new TunableRedissonCouponLockExecutor(redissonClient, waitMs, leaseMs, holdMs);
		}
	}

	static class TunableRedissonCouponLockExecutor implements CouponLockExecutor {
		private final RedissonClient client;
		private final long waitMs;
		private final long leaseMs;
		private final long holdMs;

		TunableRedissonCouponLockExecutor(RedissonClient client, long waitMs, long leaseMs, long holdMs) {
			this.client = client;
			this.waitMs = waitMs;
			this.leaseMs = leaseMs;
			this.holdMs = holdMs;
		}

		@Override
		public <T> T execute(Long couponId, Supplier<T> body) {
			RLock lock = client.getLock("lock:coupon:" + couponId);
			boolean locked = false;
			try {
				locked = lock.tryLock(waitMs, leaseMs, TimeUnit.MILLISECONDS);
				if (!locked) {
					throw new CouponIssueBusyException(ErrorCode.COUPON_ISSUE_BUSY, couponId);
				}
				else {
					// f1이 풀어줘야 f2가 들어옴. -> f1이 무조건 먼저 락을 잡는다.
					firstLockAcquired.countDown();
				}

				// 첫 스레드가 락을 오래 잡고 있게 만들어 waitTime 효과를 확실히 드러냄
				// f1이 holdMs를 길게 가져가는 동안 f2가 waitTime을 초과하게되고 CouponIssueBusyException가 발생한다.
				if (holdMs > 0) sleepSilently(holdMs);

				return body.get();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("락 대기 중 인터럽트", e);
			} finally {
				if (locked && lock.isHeldByCurrentThread()) {
					lock.unlock();
				}
			}
		}

		private static void sleepSilently(long ms) {
			try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
		}
	}
}
