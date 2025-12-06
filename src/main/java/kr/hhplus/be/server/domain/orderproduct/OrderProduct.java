package kr.hhplus.be.server.domain.orderproduct;

import java.util.Objects;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import kr.hhplus.be.server.domain.order.Order;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class OrderProduct {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	private Long productId;
	private String productNameSnap;
	private Long unitPrice;
	private int qty;

	private OrderProduct(Long productId, String productNameSnap, Long unitPrice, int qty) {
		this.productId = Objects.requireNonNull(productId);
		this.productNameSnap = Objects.requireNonNull(productNameSnap);
		this.unitPrice = Objects.requireNonNull(unitPrice);
		if(qty <= 0) throw new IllegalArgumentException("qty must be greater than zero");
		this.qty = qty;
	}
	public static OrderProduct create(Long productId, String productNameSnap, Long unitPrice, int qty) {
		return new OrderProduct(productId, productNameSnap, unitPrice, qty);
	}
	public void initOrder(Order order) {
		this.order = order;
	}
	public Long getItemTotalPrice() {
		return unitPrice * qty;
	}
}
