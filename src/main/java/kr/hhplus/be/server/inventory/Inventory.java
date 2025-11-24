package kr.hhplus.be.server.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.hhplus.be.server.product.Product;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Getter
public class Inventory {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	// unique 제약 조건 설정
	@JoinColumn(name = "product_id", nullable = false, unique = true)
	private Product product;
	@Column(nullable = false)
	private Long stock;
	@Column(nullable = false)
	private Long reserved;

	@Builder
	public Inventory(Product product, Long stock, Long reserved) {
		this.product = product;
		this.stock = stock;
		this.reserved = reserved;
	}

	public long availableStock() {
		return Math.max(stock - reserved, 0);
	}

}
