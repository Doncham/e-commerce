package kr.hhplus.be.server.application.product;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.domain.dailyProductSale.DailyProductSalesRepository;
import kr.hhplus.be.server.infrastructure.persistence.popularproductsnapshot.PopularProductSnapshotRepository;
import kr.hhplus.be.server.infrastructure.persistence.redis.PopularRankRedisWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PopularProductRefreshService {
	private final DailyProductSalesRepository dailyProductSalesRepo;
	private final PopularProductSnapshotRepository popularProductSnapshotRepo;
	private final PopularRankRedisWriter redisWriter;
	private final ObjectMapper objectMapper;
	private static final Duration TTL = Duration.ofHours(24);
	private final Clock clock;
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final int DAYS_7 = 7;
	private static final int DAYS_30 = 30;
	private static final int TOP_N = 20;
	private static final String CACHE7KEY = "rank:7d";
	private static final String CACHE30KEY = "rank:30d";

	public void refreshTopProducts() {
		// 최근 7/30일 집계
		LocalDateTime createdAt = LocalDateTime.now(clock.withZone(KST));
		List<PopularProductRowWithRank> sevenDayResult = getPopularProductRowWithRanks(DAYS_7);
		List<PopularProductRowWithRank> thirtyDayResult = getPopularProductRowWithRanks(DAYS_30);
		// 캐시 적재
		redisWriter.writeCache(CACHE7KEY, sevenDayResult, TTL);
		redisWriter.writeCache(CACHE30KEY, thirtyDayResult, TTL);
		// 스냅샷 DB 저장
		saveSnapshotToDB(sevenDayResult, createdAt);
		saveSnapshotToDB(thirtyDayResult, createdAt);
	}

	private void saveSnapshotToDB(List<PopularProductRowWithRank> sevenDayResult, LocalDateTime createdAt) {
		try {
			String sevenDayResultJson = objectMapper.writeValueAsString(sevenDayResult);
			// snapshot 저장
			popularProductSnapshotRepo.upsert(PopularDateRange.SEVEN.toString(), sevenDayResultJson, createdAt);

		} catch (JsonProcessingException e) {
			log.error("popular snapshot serialization failed. createdAt={}", createdAt, e);
			throw new IllegalStateException("popular snapshot serialization failed", e);
		}
	}

	public List<PopularProductRowWithRank> getPopularProductRowWithRanks(int day) {
		LocalDate endDate = LocalDate.now(clock.withZone(KST));
		LocalDate dayBefore = endDate.minusDays(day);

		List<PopularProductRow> topProductByPaidCount = dailyProductSalesRepo.findTopProductByPaidCount(dayBefore,
			endDate, TOP_N);

		List<PopularProductRowWithRank> result = new ArrayList<>();
		for(int i = 0; i < topProductByPaidCount.size(); i++) {
			PopularProductRow row = topProductByPaidCount.get(i);
			result.add(PopularProductRowWithRank.from(
			i + 1,
				row
			));
		}
		return result;
	}
}
