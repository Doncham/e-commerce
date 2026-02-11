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
