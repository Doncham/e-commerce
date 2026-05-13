package kr.hhplus.be.server.infrastructure.persistence.redis;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.application.product.PopularProductRowWithRank;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PopularRankRedisWriter {
	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;
	public void writeCache(String finalKey, List<PopularProductRowWithRank> rows, Duration ttl) {
		try {
			String json = objectMapper.writeValueAsString(rows == null ? List.of() : rows);

			if (ttl != null) {
				redis.opsForValue().set(finalKey, json, ttl);
			} else {
				redis.opsForValue().set(finalKey, json);
			}
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("popular cache serialize failed", e);
		}
	}
}
