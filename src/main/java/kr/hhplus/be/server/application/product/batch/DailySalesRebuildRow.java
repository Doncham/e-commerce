package kr.hhplus.be.server.application.product.batch;

import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DailySalesRebuildRow {
	private long productId;
	private long salesCount;
	private LocalDate date;
	@Builder
	public DailySalesRebuildRow(long productId, long salesCount, LocalDate date) {
		this.productId = productId;
		this.salesCount = salesCount;
		this.date = date;
	}
	public static DailySalesRebuildRow of(DailySalesAggregateRow row, LocalDate date) {
		return DailySalesRebuildRow.builder()
			.productId(row.getProductId())
			.salesCount(row.getSalesCount())
			.date(date)
			.build();
	}
}
