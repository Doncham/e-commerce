package kr.hhplus.be.server.coupon;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import kr.hhplus.be.server.FixturePersist;
import kr.hhplus.be.server.TestFixture;
import kr.hhplus.be.server.api.usercoupon.request.UserCouponCreateRequest;
import kr.hhplus.be.server.api.usercoupon.response.UserCouponCreateResponse;
import kr.hhplus.be.server.application.userCoupon.CouponLockExecutor;
import kr.hhplus.be.server.application.userCoupon.UserCouponFacade;
import kr.hhplus.be.server.domain.coupon.Coupon;
import kr.hhplus.be.server.domain.user.User;
import kr.hhplus.be.server.infrastructure.persistence.coupon.CouponRepository;
import kr.hhplus.be.server.infrastructure.persistence.user.UserRepository;
import kr.hhplus.be.server.infrastructure.persistence.userCoupon.UserCouponRepository;
@SpringBootTest
public class CouponDistributedLockTest {

	@Autowired UserRepository userRepo;
	@Autowired CouponRepository couponRepo;
	@Autowired UserCouponRepository userCouponRepo;
	@Autowired FixturePersist fixturePersist;

	// ✅ 분산락은 Facade에 있으니 Facade를 호출해야 테스트가 의미가 있음
	@Autowired UserCouponFacade userCouponFacade;

	// ✅ 우리가 심어놓은 Probe executor를 꺼내서 maxInFlight를 검증하기 위함
	@Autowired ProbingCouponLockExecutor probe;

	@Test
	void distributedLock_shouldSerializeCriticalSection_perCouponId() throws Exception {
		// given
		Coupon coupon = fixturePersist.saveAndFlush(couponRepo, TestFixture.percent10CouponForOneUser(100L));

		int threads = 30;
		ExecutorService pool = Executors.newFixedThreadPool(threads);

		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threads);

		AtomicInteger success = new AtomicInteger();
		List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

		// users+requests
		List<UserCouponCreateRequest> reqs = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			User u = fixturePersist.saveAndFlush(userRepo, TestFixture.user());
			reqs.add(UserCouponCreateRequest.of(u.getId(), coupon.getId()));
		}

		// when
		for (int i = 0; i < threads; i++) {
			final int idx = i;
			pool.submit(() -> {
				ready.countDown();
				await(start);
				try {
					UserCouponCreateResponse res = userCouponFacade.createUserCoupons(reqs.get(idx));
					success.incrementAndGet();
				} catch (Throwable t) {
					errors.add(t);
				} finally {
					done.countDown();
				}
			});
		}

		ready.await();
		start.countDown();
		done.await(10, TimeUnit.SECONDS);
		pool.shutdownNow();

		// then: 정합성(여기선 재고 100, 요청 30이므로 성공 30이 자연스러움)
		assertEquals(threads, success.get());
		assertTrue(errors.isEmpty());

		// ✅ 핵심: "동시에 임계구역 들어간 최대 개수"가 1이어야 함
		// maxInFlight: 임계구역에 들어가 있던 최대 스레드 수.
		// 락이 깨지면 2이상이 나오게 된다.
		assertEquals(1, probe.maxInFlight(),
			"분산락이 쿠폰ID 기준으로 임계구역을 직렬화해야 한다");
	}

	private static void await(CountDownLatch latch) {
		try { latch.await(); }
		catch (InterruptedException e) { Thread.currentThread().interrupt(); }
	}

	/**
	 * ✅ 테스트 컨텍스트에서만 LockExecutor를 계측용으로 감싸기
	 * - @Primary로 등록해서 CouponLockExecutor 주입 시 이 프록시가 들어가게 함
	 * - 내부에서 실제 executor(delegate)를 다시 호출
	 */
	@TestConfiguration
	static class LockProbeConfig {
		@Bean
		@Primary
		ProbingCouponLockExecutor probingLockExecutor(CouponLockExecutor delegate) {
			return new ProbingCouponLockExecutor(delegate);
		}
	}

	/**
	 * ✅ 실제 lockExecutor를 감싸서,
	 * execute 내부 임계구역에서 "동시 진입 수"를 측정한다.
	 */
	static class ProbingCouponLockExecutor implements CouponLockExecutor {
		private final CouponLockExecutor delegate;

		private final AtomicInteger inFlight = new AtomicInteger(0);
		private final AtomicInteger maxInFlight = new AtomicInteger(0);

		ProbingCouponLockExecutor(CouponLockExecutor delegate) {
			this.delegate = delegate;
		}

		@Override
		public <T> T execute(Long couponId, Supplier<T> body) {
			return delegate.execute(couponId, () -> {
				int now = inFlight.incrementAndGet();
				maxInFlight.updateAndGet(prev -> Math.max(prev, now));
				try {
					// ✅ 락이 실제로 직렬화되는지 체감되도록 일부러 지연
					sleepSilently(200);
					return body.get();
				} finally {
					inFlight.decrementAndGet();
				}
			});
		}

		int maxInFlight() {
			return maxInFlight.get();
		}

		private static void sleepSilently(long ms) {
			try { Thread.sleep(ms); }
			catch (InterruptedException e) { Thread.currentThread().interrupt(); }
		}
	}
}
