package kr.hhplus.be.server.application.product.batch;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.infrastructure.persistence.dailyproductsales.DailyProductSalesJdbcRepository;
import kr.hhplus.be.server.infrastructure.persistence.dailyproductsales.DailyProductSalesRepository;
import kr.hhplus.be.server.infrastructure.persistence.orderproduct.OrderProductRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DailyProductSalesRebuildService {
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private final Clock clock;
	private final OrderProductRepository orderProductRepository;
	private final DailyProductSalesRepository dailyProductSalesRepository;
	private final DailyProductSalesJdbcRepository jdbcRepository;

	@Transactional
	public void rebuildSalesForOneDay() {
		// 집계 조회하기
		LocalDate today = LocalDate.now(clock.withZone(KST));
		LocalDate yesterday = today.minusDays(1);

		LocalDateTime from = yesterday.atStartOfDay();
		LocalDateTime to = today.atStartOfDay();
		List<DailySalesAggregateRow> rows = orderProductRepository.rebuildDailyPopularSales(
			from, to);
		if(rows == null || rows.isEmpty()) return;

		List<DailySalesRebuildRow> newRows = rows.stream()
			.map(r -> DailySalesRebuildRow.of(r, yesterday))
			.toList();

		// 삭제하기
		dailyProductSalesRepository.deleteBySalesDate(yesterday);

		// 삽입하기
		jdbcRepository.batchInsert(newRows);
	}
}
