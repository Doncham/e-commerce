package kr.hhplus.be.server.application.firstcomeCoupon;

import static kr.hhplus.be.server.application.firstcomeCoupon.metric.CouponApplyMetricResult.*;

import java.time.Clock;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import kr.hhplus.be.server.application.firstcomeCoupon.metric.CouponApplyMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirstComeCouponService {

	private static final long ACCEPTED_RESULT = 1L;
	private static final long DUPLICATE_RESULT = 2L;
	private static final long SOLD_OUT_RESULT = 3L;
	private static final long QUANTITY_NOT_INITIALIZED_RESULT = 4L;

	private final StringRedisTemplate redis;
	private final RedisScript<Long> couponApplyScript;
	private final Clock clock;
	private final CouponIssueAsyncService couponIssueAsyncService;
	private final CouponApplyMetrics couponApplyMetrics;

	public CouponApplyResponse apply(long userId, long couponId) {
		if (couponId <= 0 || userId <= 0) {
			throw new IllegalArgumentException(
				"couponId/userId must be > 0"
			);
		}

		String reqKey = CouponRedisKeys.reqKey(couponId);
		String quantityKey =
			CouponRedisKeys.quantityKey(couponId);

		Long result;

		try {
			result = redis.execute(
				couponApplyScript,
				// KEYS[1] = reqKey, KEYS[2] = quantityKey
				List.of(reqKey, quantityKey),
				// ARGV[1] = userId, ARGV[2] = currentEpochSecond
				String.valueOf(userId),
				String.valueOf(
					clock.instant().getEpochSecond()
				)
			);
		} catch (RuntimeException exception) {
			couponApplyMetrics.increment(REDIS_ERROR);
			throw exception;
		}

		if (result == null) {
			couponApplyMetrics.increment(NULL_RESULT);

			throw new IllegalStateException(
				"Redis script returned null"
			);
		}

		if (result == ACCEPTED_RESULT) {
			couponApplyMetrics.increment(ACCEPTED);
			//System.out.println("CouponThread: " + Thread.currentThread().getId() +", || userId: " + userId + ". || couponId:" + couponId + "mili:" + System.currentTimeMillis());
			couponIssueAsyncService.issueAsync(
				userId,
				couponId
			);
			//System.out.println("CouponThread: " + Thread.currentThread().getId() +", || userId: " + userId + ". || couponId:" + couponId + "mili:" + System.currentTimeMillis());

			return CouponApplyResponse.ok(
				CouponApplyResponse.CouponApplyStatus.ACCEPTED,
				"신청 접수"
			);
		}

		if (result == DUPLICATE_RESULT) {
			couponApplyMetrics.increment(DUPLICATE);

			return CouponApplyResponse.fail(
				CouponApplyResponse.CouponApplyStatus.DUPLICATE,
				"이미 신청했습니다."
			);
		}

		if (result == SOLD_OUT_RESULT) {
			couponApplyMetrics.increment(SOLD_OUT);

			return CouponApplyResponse.fail(
				CouponApplyResponse.CouponApplyStatus.SOLD_OUT,
				"쿠폰이 모두 소진되었습니다."
			);
		}

		if (result == QUANTITY_NOT_INITIALIZED_RESULT) {
			couponApplyMetrics.increment(
				QUANTITY_NOT_INITIALIZED
			);

			throw new IllegalStateException(
				"Coupon quantity is not initialized. couponId="
					+ couponId
			);
		}

		couponApplyMetrics.increment(UNKNOWN_RESULT);

		throw new IllegalStateException(
			"Unknown script result: " + result
		);
	}
}