package kr.hhplus.be.server.application.product;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.application.product.dto.PopularProductRow;
import kr.hhplus.be.server.infrastructure.persistence.dailyproductsales.DailyProductSalesRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PopularProductQueryService {
	private final DailyProductSalesRepository dailyProductSalesRepo;
	private static final int TOP_N = 20;
	private final Clock clock;
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	@Transactional(readOnly = true)
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
