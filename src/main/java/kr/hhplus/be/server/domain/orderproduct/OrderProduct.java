package kr.hhplus.be.server.domain.orderproduct;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
	@Column(nullable = false)
	private Long productId;
	@Column(nullable = false)
	private String productNameSnap;
	@Column(nullable = false)
	private Long unitPrice;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrderProductStatus status;

	private Long appliedCouponId;
	@Column(nullable = false)
	private Long allocatedCouponDiscount;
	@Column(nullable = false)
	private Long allocatedPointUsed;

	private LocalDateTime canceledAt;


	private OrderProduct(Long productId, String productNameSnap, Long unitPrice) {
		this.productId = Objects.requireNonNull(productId);
		this.productNameSnap = Objects.requireNonNull(productNameSnap);
		this.unitPrice = Objects.requireNonNull(unitPrice);
		this.allocatedCouponDiscount = 0L;
		this.allocatedPointUsed = 0L;
		this.status = OrderProductStatus.ORDERED;
	}
	public static OrderProduct create(Long productId, String productNameSnap, Long unitPrice) {
		return new OrderProduct(productId, productNameSnap, unitPrice);
	}
	public void initOrder(Order order) {
		this.order = order;
	}

	public long getNetPaidAmount() {
		return unitPrice - allocatedCouponDiscount - allocatedPointUsed;
	}

	public void allocateDiscount(Long couponDiscount, Long pointUsed, Long couponId) {
		this.allocatedCouponDiscount = couponDiscount == null ? 0L : couponDiscount;
		this.allocatedPointUsed = pointUsed == null ? 0L : pointUsed;
		this.appliedCouponId = couponId;
		// 여기서 쿠폰으로 할인되는 금액을 계산하는것도 나쁘지 않을듯
	}
	public void cancel(LocalDateTime canceledAt) {
		if (!isCancelable()) {
			throw new IllegalStateException("already canceled orderProductId=" + id);
		}
		this.status = OrderProductStatus.CANCELED;
		this.canceledAt = canceledAt;
	}

	public boolean isCancelable() {
		return this.status == OrderProductStatus.ORDERED;
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
