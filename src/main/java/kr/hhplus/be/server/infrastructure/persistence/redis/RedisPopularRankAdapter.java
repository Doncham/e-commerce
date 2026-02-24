package kr.hhplus.be.server.infrastructure.persistence.redis;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import kr.hhplus.be.server.application.product.PopularRankPort;
import kr.hhplus.be.server.application.product.PopularScoreCodec;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class RedisPopularRankAdapter implements PopularRankPort {
	private final RedisTemplate<String, String> redisTemplate;

	@Override
	public void increment7d(Long productId, long qty) {
		increment("rank:7d", productId, qty);
	}

	@Override
	public void increment30d(Long productId, long qty) {
		increment("rank:30d", productId, qty);
	}

	private void increment(String key, Long productId, long qty) {
		if (productId == null || productId <= 0) return;
		if (qty <= 0) return;
		// deltaScore = qty * SHIFT
		double deltaScore = PopularScoreCodec.deltaForIncrement(qty);

		redisTemplate.opsForZSet().incrementScore(key, String.valueOf(productId), deltaScore);
	}
}
