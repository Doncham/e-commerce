package kr.hhplus.be.server.infrastructure.persistence.redis;

import java.time.Duration;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import kr.hhplus.be.server.application.product.PopularRankPort;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisPopularRankAdapter implements PopularRankPort {
	private final RedisTemplate<String, String> redisTemplate;
	private static final Duration DAILY_TTL = Duration.ofDays(40);

	@Override
	public void incrementDaily(String yyyymmdd, Long productId, long qty) {
		String key = "rank:daily:" + yyyymmdd;
		ZSetOperations<String, String> sortedSet = redisTemplate.opsForZSet();
		sortedSet.incrementScore(key, String.valueOf(productId), qty);
		redisTemplate.expire(key, DAILY_TTL);
	}

	@Override
	public void increment7d(Long productId, long qty) {
		ZSetOperations<String, String> sortedSet = redisTemplate.opsForZSet();
		sortedSet.incrementScore("rank:7d", String.valueOf(productId), qty);
	}

	@Override
	public void increment30d(Long productId, long qty) {
		ZSetOperations<String, String> sortedSet = redisTemplate.opsForZSet();
		sortedSet.incrementScore("rank:30d", String.valueOf(productId), qty);
	}
}
