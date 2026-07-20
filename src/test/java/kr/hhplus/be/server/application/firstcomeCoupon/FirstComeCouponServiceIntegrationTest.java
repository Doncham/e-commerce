package kr.hhplus.be.server.application.firstcomeCoupon;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class FirstComeCouponServiceIntegrationTest {

	@Autowired
	private FirstComeCouponService service;

	@Autowired
	private StringRedisTemplate redis;

	@Autowired
	private Clock clock;

	@MockitoBean
	private CouponIssueAsyncService couponIssueAsyncService;

	@BeforeEach
	void setUp() {
		deleteCouponKeys();
	}

	@AfterEach
	void tearDown() {
		deleteCouponKeys();
	}

	@Test
	void 신청_성공시_req_ZSET에_userId가_저장되고_ACCEPTED를_반환한다() {
		// given
		int couponId = 1;
		int userId = 100;

		redis.opsForValue().set(CouponRedisKeys.quantityKey(couponId), "10");

		// when
		CouponApplyResponse response = service.apply(userId, couponId);

		// then
		assertThat(statusOf(response))
			.isEqualTo(CouponApplyResponse.CouponApplyStatus.ACCEPTED);

		Set<String> users = redis.opsForZSet().range(CouponRedisKeys.reqKey(couponId), 0, -1);
		assertThat(users).containsExactly(String.valueOf(userId));

		Double score = redis.opsForZSet().score(CouponRedisKeys.reqKey(couponId), String.valueOf(userId));
		assertThat(score).isNotNull();

		verify(couponIssueAsyncService).issueAsync(userId, couponId);
	}

	@Test
	void 같은_유저가_두번_신청하면_DUPLICATE를_반환하고_ZSET_크기는_1이다() {
		// given
		long couponId = 1L;
		long userId = 100L;

		redis.opsForValue().set(CouponRedisKeys.quantityKey(couponId), "10");

		CouponApplyResponse first = service.apply(userId, couponId);
		Double firstScore = redis.opsForZSet().score(CouponRedisKeys.reqKey(couponId), String.valueOf(userId));

		// when
		CouponApplyResponse second = service.apply(userId, couponId);
		Double secondScore = redis.opsForZSet().score(CouponRedisKeys.reqKey(couponId), String.valueOf(userId));

		// then
		assertThat(statusOf(first))
			.isEqualTo(CouponApplyResponse.CouponApplyStatus.ACCEPTED);

		assertThat(statusOf(second))
			.isEqualTo(CouponApplyResponse.CouponApplyStatus.DUPLICATE);

		assertThat(secondScore).isEqualTo(firstScore);

		Long size = redis.opsForZSet().size(CouponRedisKeys.reqKey(couponId));
		assertThat(size).isEqualTo(1L);

		verify(couponIssueAsyncService).issueAsync(userId, couponId);
	}

	@Test
	void 수량이_가득차면_SOLD_OUT을_반환하고_ZSET에_추가하지_않는다() {
		// given
		long couponId = 1L;

		redis.opsForValue().set(CouponRedisKeys.quantityKey(couponId), "2");

		CouponApplyResponse first = service.apply(100L, couponId);
		CouponApplyResponse second = service.apply(101L, couponId);

		// when
		CouponApplyResponse third = service.apply(102L, couponId);

		// then
		assertThat(statusOf(first))
			.isEqualTo(CouponApplyResponse.CouponApplyStatus.ACCEPTED);
		assertThat(statusOf(second))
			.isEqualTo(CouponApplyResponse.CouponApplyStatus.ACCEPTED);
		assertThat(statusOf(third))
			.isEqualTo(CouponApplyResponse.CouponApplyStatus.SOLD_OUT);

		Set<String> users = redis.opsForZSet().range(CouponRedisKeys.reqKey(couponId), 0, -1);
		assertThat(users).containsExactlyInAnyOrder("100", "101");
		assertThat(users).doesNotContain("102");

		verify(couponIssueAsyncService).issueAsync(100L, couponId);
		verify(couponIssueAsyncService).issueAsync(101L, couponId);
		verify(couponIssueAsyncService, never()).issueAsync(102L, couponId);
	}

	@Test
	void quantityKey가_없으면_예외를_던지고_ZSET에_추가하지_않는다() {
		// given
		long couponId = 1L;
		long userId = 100L;

		// when & then
		assertThatThrownBy(() -> service.apply(userId, couponId))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Coupon quantity is not initialized");

		Long size = redis.opsForZSet().size(CouponRedisKeys.reqKey(couponId));
		assertThat(size).isEqualTo(0L);

		verify(couponIssueAsyncService, never()).issueAsync(userId, couponId);
	}

	@Test
	void 잘못된_couponId면_Redis를_변경하지_않고_예외를_던진다() {
		// when & then
		assertThatThrownBy(() -> service.apply(100L, 0L))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("couponId/userId must be > 0");

		assertThat(redis.keys("coupon:*")).isEmpty();
	}

	@Test
	void 잘못된_userId면_Redis를_변경하지_않고_예외를_던진다() {
		// when & then
		assertThatThrownBy(() -> service.apply(0L, 1L))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("couponId/userId must be > 0");

		assertThat(redis.keys("coupon:*")).isEmpty();
	}

	private void deleteCouponKeys() {
		Set<String> keys = redis.keys("coupon:*");

		if (keys != null && !keys.isEmpty()) {
			redis.delete(keys);
		}
	}



	/**
	 * CouponApplyResponse가 record면 response.status()로 바꾸면 됨.
	 */
	private CouponApplyResponse.CouponApplyStatus statusOf(CouponApplyResponse response) {
		return response.getStatus();
	}
}