package kr.hhplus.be.server.application.product.batch;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import kr.hhplus.be.server.application.product.PopularProductService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PopularProductScheduler {
	private final PopularProductService popularProductService;

	@Scheduled(cron = "0 */10 * * * *")
	public void refreshPopularProducts() {
		popularProductService.refreshTopProducts();
	}
}
