package kr.hhplus.be.server.application.firstcomeCoupon;

import java.time.Clock;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FirstComeCouponService {
	private static final long ACCEPTED = 1L;
	private static final long DUPLICATE = 2L;
	private static final long SOLD_OUT = 3L;
	private static final long QUANTITY_NOT_INITIALIZED = 4L;


	private final StringRedisTemplate redis;
	private final RedisScript<Long> couponApplyScript;
	private final Clock clock;
	public CouponApplyResponse apply(long couponId, long userId) {
		if (couponId <= 0 || userId <= 0) throw new IllegalArgumentException("couponId/userId must be > 0");

		String reqKey = reqKey(couponId);
		String quantityKey = quantityKey(couponId);

		// redis Zset에서 중복 신청 체크 + 수량 체크 + ZADD를 Lua로 감싸기
		Long result = redis.execute(
			couponApplyScript,
			List.of(reqKey, quantityKey),
			String.valueOf(userId),
			String.valueOf(clock.instant().getEpochSecond())
		);

		if (result == null) {
			throw new IllegalStateException("Redis script returned null");
		}

		if (result == ACCEPTED) {
			return CouponApplyResponse.ok(
				CouponApplyResponse.CouponApplyStatus.ACCEPTED,
				"신청 접수"
			);
		}

		if (result == DUPLICATE) {
			// @Async로 처리(전용 스레드 풀을 만들어야할듯)
			return CouponApplyResponse.fail(
				CouponApplyResponse.CouponApplyStatus.DUPLICATE,
				"이미 신청했습니다."
			);
		}

		if (result == SOLD_OUT) {
			return CouponApplyResponse.fail(
				CouponApplyResponse.CouponApplyStatus.SOLD_OUT,
				"쿠폰이 모두 소진되었습니다."
			);
		}

		if (result == QUANTITY_NOT_INITIALIZED) {
			throw new IllegalStateException("Coupon quantity is not initialized. couponId=" + couponId);
		}

		throw new IllegalStateException("Unknown script result: " + result);
	}

	private String reqKey(long couponId) {
		return "coupon:" + couponId + ":req";
	}

	private String quantityKey(long couponId) {
		return "coupon:" + couponId + ":quantity";
	}
}
