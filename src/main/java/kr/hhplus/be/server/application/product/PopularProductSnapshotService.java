package kr.hhplus.be.server.application.product;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.infrastructure.persistence.popularproductsnapshot.PopularProductSnapshotRepository;
import kr.hhplus.be.server.infrastructure.persistence.redis.PopularRankRedisWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PopularProductSnapshotService {
	private final ObjectMapper objectMapper;
	private final PopularProductSnapshotRepository popularProductSnapshotRepo;
	private final PopularRankRedisWriter redisWriter;
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void saveSnapshotToDB(PopularDateRange range, List<PopularProductRowWithRank> result, LocalDateTime createdAt) {
		try {
			String aggregateJson = objectMapper.writeValueAsString(result);
			// snapshot 저장
			popularProductSnapshotRepo.upsert(range.toString(), aggregateJson, createdAt);

		} catch (JsonProcessingException e) {
			log.error("popular snapshot serialization failed. createdAt={}", createdAt, e);
			throw new IllegalStateException("popular snapshot serialization failed", e);
		}
	}

	public void saveCacheToRedis(String cacheKey, List<PopularProductRowWithRank> content, Duration ttl) {
		redisWriter.writeCache(cacheKey, content, ttl);
	}

}
