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

	private Long salesCount;

	public DailyProductSales(LocalDate salesDate, Long productId, Long salesCount) {
		this.id = new DailyProductSalesId(salesDate, productId);
		this.salesCount = salesCount;
	}
}
