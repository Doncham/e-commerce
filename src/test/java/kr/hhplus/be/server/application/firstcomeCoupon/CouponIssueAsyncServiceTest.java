package kr.hhplus.be.server.application.firstcomeCoupon;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.awaitility.Awaitility.*;
import static org.mockito.Mockito.*;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import kr.hhplus.be.server.domain.usercoupon.UserCoupon;
import kr.hhplus.be.server.infrastructure.persistence.userCoupon.UserCouponRepository;

@SpringBootTest
@ActiveProfiles("test")
class CouponIssueAsyncServiceIntegrationTest {

	@Autowired
	private CouponIssueAsyncService couponIssueAsyncService;

	@Autowired
	private StringRedisTemplate redis;

	@MockitoBean
	private UserCouponRepository userCouponRepository;

	@BeforeEach
	void setUp() {
		deleteCouponKeys();
	}

	@AfterEach
	void tearDown() {
		deleteCouponKeys();
	}

	@Test
	void 쿠폰_발급_성공시_비동기로_DB저장_후_req_score를_1로_변경한다() {
		// given
		long userId = 100L;
		long couponId = 1L;

		AtomicReference<String> executingThreadName = new AtomicReference<>();

		doAnswer(invocation -> {
			executingThreadName.set(Thread.currentThread().getName());
			return invocation.getArgument(0);
		}).when(userCouponRepository).save(any(UserCoupon.class));

		// when
		couponIssueAsyncService.issueAsync(userId, couponId);

		// then
		verify(userCouponRepository, timeout(1000))
			.save(any(UserCoupon.class));

		await()
			.atMost(Duration.ofSeconds(2))
			.untilAsserted(() -> {
				Double score = redis.opsForZSet()
					.score(CouponRedisKeys.reqKey(couponId), String.valueOf(userId));

				assertThat(score).isEqualTo(1.0);
			});

		assertThat(executingThreadName.get())
			.startsWith("coupon-issue-");
	}

	@Test
	void 이미_발급된_쿠폰이면_unique_예외를_삼키고_req_score를_1로_변경한다() {
		// given
		long userId = 100L;
		long couponId = 1L;

		doThrow(new DataIntegrityViolationException("duplicate"))
			.when(userCouponRepository)
			.save(any(UserCoupon.class));

		// when
		couponIssueAsyncService.issueAsync(userId, couponId);

		// then
		verify(userCouponRepository, timeout(1000))
			.save(any(UserCoupon.class));

		await()
			.atMost(Duration.ofSeconds(2))
			.untilAsserted(() -> {
				Double score = redis.opsForZSet()
					.score(CouponRedisKeys.reqKey(couponId), String.valueOf(userId));

				assertThat(score).isEqualTo(1.0);
			});
	}

	@Test
	void 일반_DB예외가_발생하면_req_score를_1로_변경하지_않는다() {
		// given
		long userId = 100L;
		long couponId = 1L;

		doThrow(new RuntimeException("db down"))
			.when(userCouponRepository)
			.save(any(UserCoupon.class));

		// when
		couponIssueAsyncService.issueAsync(userId, couponId);

		// then
		verify(userCouponRepository, timeout(1000))
			.save(any(UserCoupon.class));

		await()
			.pollDelay(Duration.ofMillis(300))
			.atMost(Duration.ofSeconds(1))
			.untilAsserted(() -> {
				Double score = redis.opsForZSet()
					.score(CouponRedisKeys.reqKey(couponId), String.valueOf(userId));

				assertThat(score).isNull();
			});
	}

	private void deleteCouponKeys() {
		Set<String> keys = redis.keys("coupon:*");

		if (keys != null && !keys.isEmpty()) {
			redis.delete(keys);
		}
	}
}