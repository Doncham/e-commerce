package kr.hhplus.be.server.application.product;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import kr.hhplus.be.server.domain.dailyProductSale.DailyProductSalesRepository;
import kr.hhplus.be.server.infrastructure.persistence.redis.PopularRankRedisWriter;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PopularProductRefreshService {
	private final DailyProductSalesRepository dailyProductSalesRepo;
	private final PopularRankRedisWriter redisWriter;
	private static final Duration TTL = Duration.ofHours(24);
	private final Clock clock;
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final int DAYS_7 = 7;
	private static final int DAYS_30 = 30;
	private static final int TOP_N = 20;

	public void refreshTopProducts() {
		// 최근 7/30일 집계

		List<PopularProductRowWithRank> sevenDayResult = getPopularProductRowWithRanks(DAYS_7);
		List<PopularProductRowWithRank> thirtyDayResult = getPopularProductRowWithRanks(DAYS_30);
		// 캐시 적재
		redisWriter.writeCache("rank:7d", sevenDayResult, TTL);
		redisWriter.writeCache("rank:30d", thirtyDayResult, TTL);
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
