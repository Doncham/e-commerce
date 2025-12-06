package kr.hhplus.be.server.domain.order;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import kr.hhplus.be.server.entity.BaseTimeEntity;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.domain.user.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private OrderStatus status;

	// Address가 아닌 스냅샷
	@Embedded
	private ShippingInfo shippingAddress;
	@Column(nullable = false)
	private Long itemTotal;

	private Long couponId; // user_coupon_id, 추적, null 가능

	@Column(nullable = false)
	private Long couponDiscount;
	@Column(nullable = false)
	private Long payAmount;
	private String memo;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderProduct> orderProducts = new ArrayList<>();

	// 양방향 연관관계 메서드
	public void addOrderProduct(OrderProduct orderProduct) {
		orderProducts.add(orderProduct);
		orderProduct.initOrder(this);
	}

	private Order(User user, ShippingInfo shippingAddress, List<OrderProduct> items,
		Long couponId, Long couponDiscount, String memo) {
		this.user = Objects.requireNonNull(user);
		this.status = OrderStatus.CREATED;
		this.shippingAddress = Objects.requireNonNull(shippingAddress);
		Objects.requireNonNull(items,"order items required");
		if(items.isEmpty()){
			throw new IllegalArgumentException("order must have at least one item");
		}
		items.forEach(this::addOrderProduct);

		long itemTotal = orderProducts.stream()
			.mapToLong(OrderProduct::getItemTotalPrice)
			.sum();
		this.itemTotal = itemTotal;

		this.couponDiscount = couponDiscount == null ? 0L : couponDiscount;
		// 음수 방지
		this.payAmount = Math.max(0L, itemTotal - couponDiscount);
		this.couponId = couponId;
		this.memo = memo;
	}

	public static Order createOrder(User user, ShippingInfo shippingInfo, List<OrderProduct> items,
		Long couponId, Long couponDiscount, String memo) {
		return new Order(user, shippingInfo, items,
			couponId, couponDiscount, memo);
	}
	public void paid() {
		this.status = OrderStatus.PAID;
	}
	public void failed() {
		this.status = OrderStatus.FAILED;
	}
	public boolean isPaid() {
		return this.status == OrderStatus.PAID;
	}
	// 쿠폰 추가
}
