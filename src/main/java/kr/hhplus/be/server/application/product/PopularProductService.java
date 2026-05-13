package kr.hhplus.be.server.application.product;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PopularProductService {
	private final PopularProductSnapshotService popularProductSnapshotService;
	private final PopularProductQueryService popularProductQueryService;
	private static final Duration TTL = Duration.ofHours(24);
	private final Clock clock;
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final int DAYS_7 = 7;
	private static final int DAYS_30 = 30;
	private static final String CACHE7KEY = "rank:7d";
	private static final String CACHE30KEY = "rank:30d";


	public void refreshTopProducts() {
		// 최근 7/30일 집계 from replica
		LocalDateTime createdAt = LocalDateTime.now(clock.withZone(KST));
		List<PopularProductRowWithRank> sevenDayResult = popularProductQueryService.getPopularProductRowWithRanks(DAYS_7);
		List<PopularProductRowWithRank> thirtyDayResult = popularProductQueryService.getPopularProductRowWithRanks(DAYS_30);

		// 캐시 적재, 이건 굳이 tx 없어도 될듯.
		popularProductSnapshotService.saveCacheToRedis(CACHE7KEY, sevenDayResult, TTL);
		popularProductSnapshotService.saveCacheToRedis(CACHE30KEY, thirtyDayResult, TTL);

		// 스냅샷 DB 저장
		popularProductSnapshotService.saveSnapshotToDB(PopularDateRange.SEVEN, sevenDayResult, createdAt);
		popularProductSnapshotService.saveSnapshotToDB(PopularDateRange.THIRTY, thirtyDayResult, createdAt);
	}

}
