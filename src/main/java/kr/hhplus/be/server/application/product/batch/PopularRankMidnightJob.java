package kr.hhplus.be.server.application.product.batch;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PopularRankMidnightJob {
	private final PopularRankRebuildService dailyRebuildService;

	private static final int TOP_N = 50;

	// 매일 00:00:05 KST (혹시 자정에 DB 부하가 크면 00:10 등으로 조정)
	@Scheduled(cron = "5 0 0 * * *", zone = "Asia/Seoul")
	public void run() {
		// 1) 7d/30d 정답 리빌드
		dailyRebuildService.rebuild7d(TOP_N);
		dailyRebuildService.rebuild30d(TOP_N);
	}
}
