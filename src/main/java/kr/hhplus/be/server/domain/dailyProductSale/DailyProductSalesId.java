package kr.hhplus.be.server.domain.dailyProductSale;

import java.io.Serializable;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
@EqualsAndHashCode
public class DailyProductSalesId implements Serializable {
	// 이거 인덱스 순서는 어떻게 지정하는거냐?
	@Column(nullable = false)
	private LocalDate salesDate;
	@Column(nullable = false)
	private Long productId;

	public DailyProductSalesId(LocalDate salesDate, Long productId) {
		this.salesDate = salesDate;
		this.productId = productId;
	}
}
