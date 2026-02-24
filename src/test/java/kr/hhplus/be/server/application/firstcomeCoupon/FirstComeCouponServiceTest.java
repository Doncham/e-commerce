package kr.hhplus.be.server.application.firstcomeCoupon;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FirstComeCouponServiceTest {
	@Mock
	StringRedisTemplate redis;
	@Mock
	SetOperations<String, String> setOps;
	@Mock
	ZSetOperations<String, String> zsetOps;
	@Mock
	ValueOperations<String, String> valueOps;

	private Clock fixedClock;
	private FirstComeCouponService service;

	@BeforeEach
	void setUp() {
		fixedClock = Clock.fixed(Instant.ofEpochMilli(1_700_000_000_123L), ZoneId.of("Asia/Seoul"));
		service = new FirstComeCouponService(redis, fixedClock);

		given(redis.opsForSet()).willReturn(setOps);
		given(redis.opsForZSet()).willReturn(zsetOps);
		given(redis.opsForValue()).willReturn(valueOps);

		// TTL 설정은 반환값에 크게 의존하지 않으니 기본 true로
		given(redis.expire(anyString(), any())).willReturn(true);
	}

	@Test
	void apply_success_returnsAccepted_andEnqueuesToZset() {
		// given
		long couponId = 10L;
		long userId = 99L;
		String appliedKey = "coupon:10:applied";
		String reqKey = "coupon:10:req";
		String seqKey = "coupon:10:seq";

		given(setOps.add(appliedKey, "99")).willReturn(1L);      // 처음 신청
		given(valueOps.increment(seqKey)).willReturn(5L);        // seq 발급
		given(zsetOps.add(eq(reqKey), eq("99"), anyDouble())).willReturn(true);

		// when
		ApplyResponseDto res = service.apply(couponId, userId);

		// then
		assertThat(res).isNotNull();
		assertThat(res.isSuccess()).isTrue();
		assertThat(res.getCode()).isEqualTo(ApplyResponseDto.ApplyCode.ACCEPTED);

		// ZADD가 호출됐는지 확인 (score까지 굳이 정확히 검증할 필요 없으면 anyDouble)
		then(zsetOps).should().add(eq(reqKey), eq("99"), anyDouble());
		then(setOps).should().add(appliedKey, "99");
		then(valueOps).should().increment(seqKey);
	}

	@Test
	void apply_duplicate_returnsDuplicate_andDoesNotEnqueue() {
		// given
		long couponId = 10L;
		long userId = 99L;
		String appliedKey = "coupon:10:applied";

		given(setOps.add(appliedKey, "99")).willReturn(0L); // 이미 신청

		// when
		ApplyResponseDto res = service.apply(couponId, userId);

		// then
		assertThat(res).isNotNull();
		assertThat(res.isSuccess()).isFalse();
		assertThat(res.getCode()).isEqualTo(ApplyResponseDto.ApplyCode.DUPLICATE);

		// ZSET/INCR은 호출되면 안 됨
		then(redis).should(never()).opsForZSet();
		then(redis).should(never()).opsForValue();
	}

	@Test
	void apply_whenIncrReturnsNull_thenCompensateAndThrow() {
		// given
		long couponId = 10L;
		long userId = 99L;
		String appliedKey = "coupon:10:applied";
		String seqKey = "coupon:10:seq";

		given(setOps.add(appliedKey, "99")).willReturn(1L);
		given(valueOps.increment(seqKey)).willReturn(null); // 장애 상황

		// when / then
		assertThatThrownBy(() -> service.apply(couponId, userId))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("Redis INCR returned null");

		// 보상: applied에서 제거했는지 확인
		then(setOps).should().remove(appliedKey, "99");

		// ZSET enqueue는 호출되면 안 됨
		then(redis).should(never()).opsForZSet();
	}
}