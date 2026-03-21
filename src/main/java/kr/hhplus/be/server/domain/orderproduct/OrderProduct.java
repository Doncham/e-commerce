package kr.hhplus.be.server.domain.orderproduct;

import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import kr.hhplus.be.server.domain.cartItem.CartItem;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class OrderProduct extends BaseTimeEntity{
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	private Long productId;
	private String productNameSnap;
	private Long unitPrice;

	private long canceledQty;
	private Long allocatedCouponDiscount;
	private Long allocatedPointUsed;


	private OrderProduct(Long productId, String productNameSnap, Long unitPrice) {
		this.productId = Objects.requireNonNull(productId);
		this.productNameSnap = Objects.requireNonNull(productNameSnap);
		this.unitPrice = Objects.requireNonNull(unitPrice);
	}
	public static OrderProduct create(Long productId, String productNameSnap, Long unitPrice) {
		return new OrderProduct(productId, productNameSnap, unitPrice);
	}
	public void initOrder(Order order) {
		this.order = order;
	}

	public static List<OrderProduct> createFromCartItem(CartItem cartItem) {
		long qty = cartItem.getQty();
		if (qty <= 0) {
			throw new IllegalArgumentException("CartItem qty must be positive");
		}
		return IntStream.range(0, (int)qty)
			.mapToObj(i -> OrderProduct.create(
				cartItem.getProduct().getId(),
				cartItem.getProduct().getName(),
				cartItem.getProduct().getPrice()
			))
			.toList();
	}
}
