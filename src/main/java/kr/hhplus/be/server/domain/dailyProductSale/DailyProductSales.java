package kr.hhplus.be.server.domain.dailyProductSale;

import java.time.LocalDate;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import kr.hhplus.be.server.entity.BaseTimeEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class DailyProductSales extends BaseTimeEntity {
	@EmbeddedId
	private DailyProductSalesId id;

	private Long saleCount;

	public DailyProductSales(LocalDate salesDate, Long productId, Long saleCount) {
		this.id = new DailyProductSalesId(salesDate, productId);
		this.saleCount = saleCount;
	}
}
