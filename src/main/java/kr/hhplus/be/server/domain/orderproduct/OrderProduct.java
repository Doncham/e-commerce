package kr.hhplus.be.server.domain.orderproduct;

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
import kr.hhplus.be.server.domain.paymentcancel.PaymentCancel;
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

	@Column(nullable = true)
	private Long appliedCouponId;
	@Column(nullable = false)
	private Long allocatedCouponDiscount;
	@Column(nullable = false)
	private Long allocatedPointUsed;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "payment_cancel_id")
	private PaymentCancel paymentCancel;


	private OrderProduct(Long productId, String productNameSnap, Long unitPrice, Long couponId) {
		this.productId = Objects.requireNonNull(productId);
		this.productNameSnap = Objects.requireNonNull(productNameSnap);
		this.unitPrice = Objects.requireNonNull(unitPrice);
		this.appliedCouponId = couponId;
		this.allocatedCouponDiscount = 0L;
		this.allocatedPointUsed = 0L;
		this.status = OrderProductStatus.ORDERED;
	}
	public static OrderProduct create(Long productId, String productNameSnap, Long unitPrice, Long couponId) {
		return new OrderProduct(productId, productNameSnap, unitPrice, couponId);
	}
	public void initOrder(Order order) {
		this.order = order;
	}

	public long getCancelableAmount() {
		return unitPrice - allocatedCouponDiscount - allocatedPointUsed;
	}

	public void cancel(PaymentCancel paymentCancel) {
		if (!isCancelable()) {
			throw new IllegalStateException("already canceled orderProductId=" + id);
		}
		this.status = OrderProductStatus.CANCELED;
		this.paymentCancel = paymentCancel;
	}

	public boolean isCancelable() {
		return this.status == OrderProductStatus.ORDERED;
	}


	public static List<OrderProduct> createFromCartItem(CartItem cartItem, long orderQty, Long couponId) {

		if (orderQty <= 0) {
			throw new IllegalArgumentException("orderQty must be positive");
		}
		return IntStream.range(0, (int) orderQty)
			.mapToObj(i -> OrderProduct.create(
				cartItem.getProduct().getId(),
				cartItem.getProduct().getName(),
				cartItem.getProduct().getPrice(),
				// 이것도 포인트처럼 별도의 allocator로 처리하는게 좋다.(일단 쿠폰 정책 없으니까 유지)
				couponId
			))
			.toList();
	}

	public void allocatePoint(long pointAmount) {
		if (pointAmount < 0) {
			throw new IllegalArgumentException("pointAmount must be >= 0");
		}
		if (pointAmount > this.unitPrice - this.allocatedCouponDiscount) {
			throw new IllegalArgumentException("pointAmount cannot exceed unitPrice");
		}
		this.allocatedPointUsed = pointAmount;
	}
}
