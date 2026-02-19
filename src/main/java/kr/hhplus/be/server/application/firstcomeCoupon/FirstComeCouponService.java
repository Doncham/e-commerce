package kr.hhplus.be.server.application.firstcomeCoupon;

import java.time.Clock;
import java.time.Duration;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
/*
*
* Redis keys (couponId 기준):
* - coupon:{id}:req        (ZSET)  : 선착순 대기열 (member=userId, score=timestamp+seq)
* - coupon:{id}:applied    (SET)   : 신청 중복 방지(1인 1회 신청)
* - coupon:{id}:seq        (STRING): 동일 ms tie-break용 시퀀스
*
*/
public class FirstComeCouponService {
	private static final long SHIFT = 1_000L;
	private static final Duration APPLY_TTL = Duration.ofDays(2);
	private final StringRedisTemplate redis;
	private final Clock clock;
	public ApplyResponseDto apply(long couponId, long userId) {
		if (couponId <= 0 || userId <= 0) throw new IllegalArgumentException("couponId/userId must be > 0");

		final String appliedKey = appliedKey(couponId);
		final String reqKey = reqKey(couponId);
		final String seqKey = seqKey(couponId);


		// 1) 신청 중복 방지
		// 처음 신청 시 1, 이미 신청했으면 0
		Long added = redis.opsForSet().add(appliedKey, String.valueOf(userId));
		// redis 장애 시
		if(added == null) throw new IllegalStateException("Redis SET add returned null");

		if (added == 0L) {
			// 이미 신청한 유저
			return ApplyResponseDto.fail(ApplyResponseDto.ApplyCode.DUPLICATE,"이미 신청했습니다.");
		}
		// 이벤트 후 자동 청소
		redis.expire(appliedKey, APPLY_TTL);

		// 2) 선착순 큐잉 (ZSET)
		long nowMs = clock.millis();

		// 전역 seq 증가(원자적). 타이브레이커 역할
		Long seq = redis.opsForValue().increment(seqKey);
		if (seq == null) {
			// seq를 못 만들었으면 신청 자체를 실패 처리(보상)
			redis.opsForSet().remove(appliedKey, String.valueOf(userId));
			throw new IllegalStateException("Redis INCR returned null");
		}
		redis.expire(seqKey, APPLY_TTL);

		long packed = nowMs * SHIFT + (seq % SHIFT);
		double score = (double) packed;
		Boolean ok = redis.opsForZSet().add(reqKey, String.valueOf(userId), score);
		if (ok == null || !ok) {
			// 큐잉 실패 시 보상
			redis.opsForZSet().remove(appliedKey, String.valueOf(userId));
			throw new IllegalStateException("Failed to enqueue request into ZSET");
		}
		redis.expire(reqKey, APPLY_TTL);

		return ApplyResponseDto.ok(ApplyResponseDto.ApplyCode.ACCEPTED,"신청 접수");

	}

	private String reqKey(long couponId) {
		return "coupon:" + couponId + ":req";
	}

	private String appliedKey(long couponId) {
		return "coupon:" + couponId + ":applied";
	}

	private String seqKey(long couponId) {
		return "coupon:" + couponId + ":seq";
	}
}
