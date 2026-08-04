package kr.hhplus.be.server.domain.inventory;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import kr.hhplus.be.server.entity.BaseTimeEntity;
import kr.hhplus.be.server.domain.product.Product;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Getter
public class Inventory extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	// unique 제약 조건 설정
	@JoinColumn(name = "product_id", nullable = false, unique = true)
	private Product product;

	@Column(nullable = false)
	private long stock;

	@Column(nullable = false)
	private long reserved;

	@Column(name = "deleted_at")
	private LocalDateTime deletedAt;


	private Inventory(Product product, Long stock) {
		if (stock < 0) {
			throw new IllegalArgumentException(
				"Initial stock cannot be negative."
			);
		}

		this.product = product;
		this.stock = stock;
		this.reserved = 0L;
	}

	public static Inventory of(Product product, Long stock) {
		return new Inventory(product, stock);
	}

	public long availableStock() {
		// 아래 코드의 경우 reserved:8 stock:5 처럼 정합성이 깨진 경우가 숨겨질 수 있다.
		// return Math.max(stock - reserved, 0);
		validateStockInvariant();
		return stock - reserved;
	}

	public void reserveStock(long qty) {
		validatePositiveQuantity(qty);
		validateStockInvariant();

		if (availableStock() < qty) {
			throw new IllegalStateException(
				"Insufficient stock to reserve. "
					+ "inventoryId=" + id
					+ ", available=" + availableStock()
					+ ", requested=" + qty
			);
		}

		reserved += qty;
	}

	public void releaseReserve(Long qty) {
		validatePositiveQuantity(qty);
		validateStockInvariant();

		if (reserved < qty) {
			throw new IllegalStateException(
				"Release quantity exceeds reserved quantity. "
					+ "inventoryId=" + id
					+ ", reserved=" + reserved
					+ ", releaseQty=" + qty
			);
		}
		reserved -= qty;
	}



	public void confirmReserve(long qty) {
		validatePositiveQuantity(qty);
		validateStockInvariant();

		if (reserved < qty) {
			throw new IllegalStateException(
				"Confirm quantity exceeds reserved quantity. "
					+ "inventoryId=" + id
					+ ", reserved=" + reserved
					+ ", confirmQty=" + qty
			);
		}

		if (stock < qty) {
			throw new IllegalStateException(
				"Confirm quantity exceeds stock. "
					+ "inventoryId=" + id
					+ ", stock=" + stock
					+ ", confirmQty=" + qty
			);
		}

		reserved -= qty;
		stock -= qty;
	}
	public void restoreOne() {
		stock++;
	}

	private void validateStockInvariant() {
		if (stock < 0 || reserved < 0 || reserved > stock) {
			throw new IllegalStateException(
				"Invalid inventory state. "
					+ "inventoryId=" + id
					+ ", stock=" + stock
					+ ", reserved=" + reserved
			);
		}
	}

	private void validatePositiveQuantity(long qty) {
		if (qty <= 0) {
			throw new IllegalArgumentException(
				"Quantity must be positive. qty=" + qty
			);
		}
	}
}
