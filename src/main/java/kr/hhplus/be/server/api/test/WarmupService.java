package kr.hhplus.be.server.api.test;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.infrastructure.persistence.userCoupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class WarmupService {

	private final ObjectMapper objectMapper;
	private final StringRedisTemplate redis;
	private final RedisScript<Long> couponApplyScript;
	private final Clock clock;
	private final AsyncWarmupService asyncWarmupService;

	private final AtomicLong warmupSeq = new AtomicLong();

	@Transactional
	public void warmupFull() {
		// 1. JPA / Transaction / MySQL driver 경로 warm-up + Async까지
		asyncWarmupService.warmupAsync(1L);

		// 2. Redis / Redisson / Netty 경로 warm-up
		long seq = warmupSeq.incrementAndGet();
		redis.execute(
			couponApplyScript,
			// 2번째 key는 실제로 테스트에서 쓰는 key지만 조회만 함.
			List.of("warmup:req", "coupon:1:quantity"),
			// 유저가 중복되면 중복 발급 응답 반환
			"warmup-user" + seq,
			String.valueOf(clock.instant().getEpochSecond())
		);

		// 3. dummy key로 Redis ZSET ADD
		redis.opsForZSet().add(
			"warmup:coupon:req",
			"warmup-user-" + seq,
			1
		);

		// 4. Jackson 직렬화 경로 warm-up
		try {
			objectMapper.writeValueAsString(
				new WarmupResponse("ok", System.currentTimeMillis())
			);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Warm-up serialization failed", e);
		}
	}

	public void deleteKey() {
		redis.delete("warmup:req");
		redis.delete("warmup:coupon:req");
	}

	public record WarmupResponse(String status, long timestamp) {
	}
}
