package kr.hhplus.be.server.application.firstcomeCoupon;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import kr.hhplus.be.server.application.firstcomeCoupon.worker.FirstComeCouponWorker;
import kr.hhplus.be.server.domain.usercoupon.UserCoupon;
import kr.hhplus.be.server.infrastructure.persistence.userCoupon.UserCouponRepository;

@SpringBootTest(
	properties = {
		// ✅ 테스트에서 스케줄러가 자동 실행되지 않게 차단
		"spring.task.scheduling.enabled=false"
	}
)
@ActiveProfiles("test")
class FirstComeCouponWorkerIntegrationTest {

	@Autowired
	private FirstComeCouponWorker worker;

	@Autowired
	private StringRedisTemplate redis;

	@Autowired
	private RedissonClient redisson;

	@Autowired
	private Clock clock;

	@MockitoBean
	private UserCouponRepository userCouponRepository;

	@BeforeEach
	void setUp() {
		deleteTestKeys();
	}

	@AfterEach
	void tearDown() {
		deleteTestKeys();
	}

	@Test
	void pop이_비어있으면_req에서_pop으로_복사한다() {
		// given
		long couponId = 1L;
		long oldScore = clock.instant().getEpochSecond() - 20;

		redis.opsForSet().add(CouponRedisKeys.eventCouponKey(), String.valueOf(couponId));
		redis.opsForZSet().add(CouponRedisKeys.reqKey(couponId), "100", oldScore);
		redis.opsForZSet().add(CouponRedisKeys.reqKey(couponId), "101", oldScore + 1);

		// when
		worker.issueCoupon();

		// then
		Set<String> popUsers = redis.opsForZSet()
			.range(CouponRedisKeys.popKey(couponId), 0, -1);

		assertThat(popUsers).containsExactlyInAnyOrder("100", "101");

		verify(userCouponRepository, never()).save(any(UserCoupon.class));
	}

	@Test
	void pop에_유저가_있으면_DB저장_후_req_score를_1로_변경한다() {
		// given
		long couponId = 1L;
		long oldScore = clock.instant().getEpochSecond() - 20;

		redis.opsForSet().add(CouponRedisKeys.eventCouponKey(), String.valueOf(couponId));

		redis.opsForZSet().add(CouponRedisKeys.reqKey(couponId), "100", oldScore);
		redis.opsForZSet().add(CouponRedisKeys.popKey(couponId), "100", oldScore);

		// when
		worker.issueCoupon();

		// then
		verify(userCouponRepository).save(any(UserCoupon.class));

		Double reqScore = redis.opsForZSet()
			.score(CouponRedisKeys.reqKey(couponId), "100");

		assertThat(reqScore).isEqualTo(1.0);

		Set<String> popUsers = redis.opsForZSet()
			.range(CouponRedisKeys.popKey(couponId), 0, -1);

		assertThat(popUsers).doesNotContain("100");
	}

	@Test
	void 이미_발급된_유저면_unique_예외가_나도_req_score를_1로_변경한다() {
		// given
		long couponId = 1L;
		long oldScore = clock.instant().getEpochSecond() - 20;

		redis.opsForSet().add(CouponRedisKeys.eventCouponKey(), String.valueOf(couponId));

		redis.opsForZSet().add(CouponRedisKeys.reqKey(couponId), "100", oldScore);
		redis.opsForZSet().add(CouponRedisKeys.popKey(couponId), "100", oldScore);

		doThrow(new DataIntegrityViolationException("duplicate"))
			.when(userCouponRepository)
			.save(any(UserCoupon.class));

		// when
		worker.issueCoupon();

		// then
		Double reqScore = redis.opsForZSet()
			.score(CouponRedisKeys.reqKey(couponId), "100");

		assertThat(reqScore).isEqualTo(1.0);
	}

	@Test
	void 일반_DB예외가_발생하면_req_score를_그대로_둔다() {
		// given
		long couponId = 1L;
		long oldScore = clock.instant().getEpochSecond() - 20;

		redis.opsForSet().add(CouponRedisKeys.eventCouponKey(), String.valueOf(couponId));

		redis.opsForZSet().add(CouponRedisKeys.reqKey(couponId), "100", oldScore);
		redis.opsForZSet().add(CouponRedisKeys.popKey(couponId), "100", oldScore);

		doThrow(new RuntimeException("db down"))
			.when(userCouponRepository)
			.save(any(UserCoupon.class));

		// when
		worker.issueCoupon();

		// then
		Double reqScore = redis.opsForZSet()
			.score(CouponRedisKeys.reqKey(couponId), "100");

		assertThat(reqScore).isEqualTo((double) oldScore);
	}

	@Test
	void 다른_스레드가_lock을_잡고_있으면_req에서_pop으로_복사하지_않는다() throws Exception {
		// given
		long couponId = 1L;
		long oldScore = clock.instant().getEpochSecond() - 20;

		redis.opsForSet().add(CouponRedisKeys.eventCouponKey(), String.valueOf(couponId));
		redis.opsForZSet().add(CouponRedisKeys.reqKey(couponId), "100", oldScore);

		String lockName = CouponRedisKeys.lockKey(couponId);
		RLock lock = redisson.getLock(lockName);

		ExecutorService executor = Executors.newSingleThreadExecutor();
		CountDownLatch lockedLatch = new CountDownLatch(1);
		CountDownLatch releaseLatch = new CountDownLatch(1);

		executor.submit(() -> {
			lock.lock(10, TimeUnit.SECONDS);
			try {
				lockedLatch.countDown();
				releaseLatch.await(5, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			} finally {
				if (lock.isHeldByCurrentThread()) {
					lock.unlock();
				}
			}
		});

		assertThat(lockedLatch.await(3, TimeUnit.SECONDS)).isTrue();

		try {
			// when
			worker.issueCoupon();

			// then
			Set<String> popUsers = redis.opsForZSet()
				.range(CouponRedisKeys.popKey(couponId), 0, -1);

			assertThat(popUsers).isEmpty();

		} finally {
			releaseLatch.countDown();
			executor.shutdownNow();
		}
	}

	private void deleteTestKeys() {
		deleteByPattern("coupon:*");
		deleteByPattern("lock:coupon:*");
	}

	private void deleteByPattern(String pattern) {
		Set<String> keys = redis.keys(pattern);

		if (keys != null && !keys.isEmpty()) {
			redis.delete(keys);
		}
	}
}