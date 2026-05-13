package kr.hhplus.be.server.application.product.batch;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PopularRankMidnightJob {
	private final DailyProductSalesRebuildService dailyRebuildService;


	// 매일 00:00:05 KST (혹시 자정에 DB 부하가 크면 00:10 등으로 조정)
	@Scheduled(cron = "5 0 0 * * *", zone = "Asia/Seoul")
	public void rebuildDailyProductSalesForOneDay() {
		dailyRebuildService.rebuildSalesForOneDay();
	}
}
