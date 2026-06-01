package kr.hhplus.be.server.application.firstcomeCoupon.worker;

import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import kr.hhplus.be.server.application.firstcomeCoupon.CouponRedisKeys;
import kr.hhplus.be.server.domain.usercoupon.UserCoupon;
import kr.hhplus.be.server.infrastructure.persistence.userCoupon.UserCouponRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FirstComeCouponWorkerTest {

	@Mock
	private UserCouponRepository userCouponRepository;

	@Mock
	private StringRedisTemplate redis;

	@Mock
	private RedissonClient redisson;

	@Mock
	private SetOperations<String, String> setOps;

	@Mock
	private ZSetOperations<String, String> zSetOps;

	@Mock
	private RLock lock;

	private FirstComeCouponWorker worker;

	private final Clock fixedClock = Clock.fixed(
		Instant.parse("2026-05-30T00:00:20Z"),
		ZoneId.of("Asia/Seoul")
	);

	@BeforeEach
	void setUp() {
		worker = new FirstComeCouponWorker(
			userCouponRepository,
			redis,
			fixedClock,
			redisson
		);

		when(redis.opsForSet()).thenReturn(setOps);
		when(redis.opsForZSet()).thenReturn(zSetOps);
	}

	@Test
	void 이벤트_쿠폰이_없으면_아무것도_하지_않는다() {
		// given
		when(setOps.members(CouponRedisKeys.eventCouponKey()))
			.thenReturn(Set.of());

		// when
		worker.issueCoupon();

		// then
		verify(zSetOps, never()).popMin(anyString(), anyInt());
		verify(userCouponRepository, never()).save(any(UserCoupon.class));
	}

	@Test
	void pop에_유저가_있으면_DB에_저장하고_req_score를_1로_변경한다() {
		// given
		String couponId = "1";
		String userId = "100";

		when(setOps.members(CouponRedisKeys.eventCouponKey()))
			.thenReturn(Set.of(couponId));

		Set<ZSetOperations.TypedTuple<String>> popUsers = Set.of(
			ZSetOperations.TypedTuple.of(userId, 10.0)
		);

		when(zSetOps.popMin(CouponRedisKeys.popKey(1), 10))
			.thenReturn(popUsers);

		// when
		worker.issueCoupon();

		// then
		verify(userCouponRepository).save(any(UserCoupon.class));
		verify(zSetOps).add(CouponRedisKeys.reqKey(1), userId, 1.0);
	}

	@Test
	void unique_예외가_발생해도_req_score를_1로_변경한다() {
		// given
		String couponId = "1";
		String userId = "100";

		when(setOps.members(CouponRedisKeys.eventCouponKey()))
			.thenReturn(Set.of(couponId));

		Set<ZSetOperations.TypedTuple<String>> popUsers = Set.of(
			ZSetOperations.TypedTuple.of(userId, 10.0)
		);

		when(zSetOps.popMin(CouponRedisKeys.popKey(1), 10))
			.thenReturn(popUsers);

		doThrow(new DataIntegrityViolationException("duplicate"))
			.when(userCouponRepository)
			.save(any(UserCoupon.class));

		// when
		worker.issueCoupon();

		// then
		verify(zSetOps).add(CouponRedisKeys.reqKey(1), userId, 1.0);
	}

	@Test
	void 일반_예외가_발생하면_req_score를_변경하지_않는다() {
		// given
		String couponId = "1";
		String userId = "100";

		when(setOps.members(CouponRedisKeys.eventCouponKey()))
			.thenReturn(Set.of(couponId));

		Set<ZSetOperations.TypedTuple<String>> popUsers = Set.of(
			ZSetOperations.TypedTuple.of(userId, 10.0)
		);

		when(zSetOps.popMin(CouponRedisKeys.popKey(1), 10))
			.thenReturn(popUsers);

		doThrow(new RuntimeException("db down"))
			.when(userCouponRepository)
			.save(any(UserCoupon.class));

		// when
		worker.issueCoupon();

		// then
		verify(zSetOps, never()).add(CouponRedisKeys.reqKey(1), userId, 1.0);
	}

	@Test
	void pop이_비어있고_lock을_잡으면_req에서_pop으로_복사한다() throws Exception {
		// given
		String couponId = "1";

		when(setOps.members(CouponRedisKeys.eventCouponKey()))
			.thenReturn(Set.of(couponId));

		when(zSetOps.popMin(CouponRedisKeys.popKey(1), 10))
			.thenReturn(Set.of());

		when(redisson.getLock("lock:coupon:" + couponId))
			.thenReturn(lock);

		when(lock.tryLock(0, 30, TimeUnit.SECONDS))
			.thenReturn(true);

		when(lock.isHeldByCurrentThread())
			.thenReturn(true);

		Set<ZSetOperations.TypedTuple<String>> reqUsers = Set.of(
			ZSetOperations.TypedTuple.of("100", 10.0),
			ZSetOperations.TypedTuple.of("101", 11.0)
		);

		when(zSetOps.rangeByScoreWithScores(
			eq(CouponRedisKeys.reqKey(1)),
			eq(2.0),
			eq((double) fixedClock.instant().getEpochSecond() - 10),
			eq(0L),
			eq(100L)
		)).thenReturn(reqUsers);

		// when
		worker.issueCoupon();

		// then
		verify(zSetOps).add(eq(CouponRedisKeys.popKey(1)), anySet());
		verify(lock).unlock();
		verify(userCouponRepository, never()).save(any(UserCoupon.class));
	}

	@Test
	void pop이_비어있고_lock을_못잡으면_copy하지_않는다() throws Exception {
		// given
		String couponId = "1";

		when(setOps.members(CouponRedisKeys.eventCouponKey()))
			.thenReturn(Set.of(couponId));

		when(zSetOps.popMin(CouponRedisKeys.popKey(1), 10))
			.thenReturn(Set.of());

		when(redisson.getLock("lock:coupon:" + couponId))
			.thenReturn(lock);

		when(lock.tryLock(0, 30, TimeUnit.SECONDS))
			.thenReturn(false);

		// when
		worker.issueCoupon();

		// then
		verify(zSetOps, never()).rangeByScoreWithScores(
			anyString(),
			anyDouble(),
			anyDouble(),
			anyLong(),
			anyLong()
		);
		verify(zSetOps, never()).add(eq(CouponRedisKeys.popKey(1)), anySet());
		verify(lock, never()).unlock();
	}
}
