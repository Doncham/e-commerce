package kr.hhplus.be.server.application.product.batch;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import kr.hhplus.be.server.application.product.PopularProductRefreshService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PopularProductScheduler {
	private final PopularProductRefreshService popularProductRefreshService;

	@Scheduled(cron = "0 */10 * * * *")
	public void refreshPopularProducts() {
		popularProductRefreshService.refreshTopProducts();
	}
}
