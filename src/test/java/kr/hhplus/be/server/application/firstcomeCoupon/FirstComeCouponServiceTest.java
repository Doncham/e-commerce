package kr.hhplus.be.server.application.firstcomeCoupon;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.mockito.BDDMockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

@ExtendWith(MockitoExtension.class)
class FirstComeCouponServiceTest {

	private static final long ACCEPTED = 1L;
	private static final long DUPLICATE = 2L;
	private static final long SOLD_OUT = 3L;
	private static final long QUANTITY_NOT_INITIALIZED = 4L;

	@Mock
	private StringRedisTemplate redis;

	@Mock
	private RedisScript<Long> couponApplyScript;

	@Mock
	private CouponIssueAsyncService couponIssueAsyncService;

	private FirstComeCouponService service;

	private final Clock fixedClock = Clock.fixed(
		Instant.parse("2026-05-30T00:00:00Z"),
		ZoneId.of("Asia/Seoul")
	);

	@BeforeEach
	void setUp() {
		service = new FirstComeCouponService(
			redis,
			couponApplyScript,
			fixedClock,
			couponIssueAsyncService
		);
	}

	@Test
	void 신청_성공이면_ACCEPTED를_반환하고_비동기_발급을_호출한다() {
		// given
		long userId = 20L;
		long couponId = 10L;

		givenRedisScriptResult(ACCEPTED);

		// when
		CouponApplyResponse response = service.apply(userId, couponId);

		// then
		assertThat(response).isNotNull();
		// DTO 구조에 맞게 getStatus(), status() 등으로 수정
		assertThat(response.getStatus()).isEqualTo(CouponApplyResponse.CouponApplyStatus.ACCEPTED);

		verify(redis).execute(
			eq(couponApplyScript),
			eq(List.of("coupon:10:req", "coupon:10:quantity")),
			eq("20"),
			eq(String.valueOf(fixedClock.instant().getEpochSecond()))
		);

		verify(couponIssueAsyncService).issueAsync(userId, couponId);
	}

	@Test
	void 중복_신청이면_DUPLICATE를_반환하고_비동기_발급을_호출하지_않는다() {
		// given
		long userId = 20L;
		long couponId = 10L;

		givenRedisScriptResult(DUPLICATE);

		// when
		CouponApplyResponse response = service.apply(userId, couponId);

		// then
		assertThat(response).isNotNull();
		assertThat(response.getStatus()).isEqualTo(CouponApplyResponse.CouponApplyStatus.DUPLICATE);

		verify(couponIssueAsyncService, never()).issueAsync(userId, couponId);
	}

	@Test
	void 수량_부족이면_SOLD_OUT을_반환하고_비동기_발급을_호출하지_않는다() {
		// given
		long userId = 20L;
		long couponId = 10L;

		givenRedisScriptResult(SOLD_OUT);

		// when
		CouponApplyResponse response = service.apply(userId, couponId);

		// then
		assertThat(response).isNotNull();
		assertThat(response.getStatus()).isEqualTo(CouponApplyResponse.CouponApplyStatus.SOLD_OUT);

		verify(couponIssueAsyncService, never()).issueAsync(userId, couponId);
	}

	@Test
	void 쿠폰_수량이_초기화되지_않았으면_예외를_던진다() {
		// given
		long userId = 20L;
		long couponId = 10L;

		givenRedisScriptResult(QUANTITY_NOT_INITIALIZED);

		// when & then
		assertThatThrownBy(() -> service.apply(userId, couponId))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Coupon quantity is not initialized");

		verify(couponIssueAsyncService, never()).issueAsync(userId, couponId);
	}

	@Test
	void Redis_script_결과가_null이면_예외를_던진다() {
		// given
		long userId = 20L;
		long couponId = 10L;

		givenRedisScriptResult(null);

		// when & then
		assertThatThrownBy(() -> service.apply(userId, couponId))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Redis script returned null");

		verify(couponIssueAsyncService, never()).issueAsync(userId, couponId);
	}

	@Test
	void 알수없는_script_결과면_예외를_던진다() {
		// given
		long userId = 20L;
		long couponId = 10L;

		givenRedisScriptResult(999L);

		// when & then
		assertThatThrownBy(() -> service.apply(userId, couponId))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Unknown script result");

		verify(couponIssueAsyncService, never()).issueAsync(userId, couponId);
	}

	@Test
	void couponId가_0이하면_예외를_던지고_Redis를_호출하지_않는다() {
		// when & then
		assertThatThrownBy(() -> service.apply(1L, 0L))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("couponId/userId must be > 0");

		verifyNoInteractions(redis);
		verifyNoInteractions(couponIssueAsyncService);
	}

	@Test
	void userId가_0이하면_예외를_던지고_Redis를_호출하지_않는다() {
		// when & then
		assertThatThrownBy(() -> service.apply(0L, 1L))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("couponId/userId must be > 0");

		verifyNoInteractions(redis);
		verifyNoInteractions(couponIssueAsyncService);
	}

	private void givenRedisScriptResult(Long result) {
		doReturn(result)
			.when(redis)
			.execute(
				eq(couponApplyScript),
				anyList(),
				org.mockito.ArgumentMatchers.<Object>any(),
				org.mockito.ArgumentMatchers.<Object>any()
			);
	}
}