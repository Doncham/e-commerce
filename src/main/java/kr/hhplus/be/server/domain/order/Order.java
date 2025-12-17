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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.hhplus.be.server.entity.BaseTimeEntity;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.domain.user.User;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
	uniqueConstraints = @UniqueConstraint(
		name = "ux_userid_and_idempotencyKey",
		columnNames = {"user_id", "idempotency_key"}
	)
)
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
	// 이것도 차감해줘야지
	@Column(nullable = false)
	private Long pointUsed;
	@Column(name="idempotency_key", nullable = false)
	private String idempotencyKey;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderProduct> orderProducts = new ArrayList<>();

	public static Order createDraft(User user, ShippingInfo shippingInfo, String idempotencyKey) {
		return new Order(user, shippingInfo, idempotencyKey);
	}
	private Order(User user, ShippingInfo shippingInfo, String idempotencyKey) {
		this.user = Objects.requireNonNull(user);
		this.shippingAddress = Objects.requireNonNull(shippingInfo);
		this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
		this.status = OrderStatus.DRAFT;
		this.itemTotal = 0L;
		this.couponDiscount = 0L;
		this.payAmount = 0L;
		this.pointUsed = 0L;
	}


	public void completeOrderDraft(
		List<OrderProduct> items,
		Long couponId,
		Long couponDiscount,
		String memo,
		Long point
	) {
		ensureDraftState();
		// null, empty 체크
		validateItems(items);
		items.forEach(this::addOrderProduct);

		// item 총 가격 계산
		long itemTotal = calculateItemTotal();
		this.itemTotal = itemTotal;

		this.couponId = couponId;
		this.couponDiscount = couponDiscount == null ? 0L : couponDiscount;
		this.pointUsed = point == null ? 0L : point;
		long discounted = Math.max(0L, this.itemTotal - this.couponDiscount - this.pointUsed);
		this.payAmount = discounted;

		this.memo = memo;

		this.status = OrderStatus.CREATED;
	}
	private void ensureDraftState() {
		if(this.status != OrderStatus.DRAFT){
			throw new IllegalStateException("Only draft order can be completed");
		}
	}

	private void validateItems(List<OrderProduct> items) {
		Objects.requireNonNull(items, "order items required");
		if(items.isEmpty()) throw new IllegalArgumentException("order must have at least one item");
	}

	private long calculateItemTotal() {
		return this.orderProducts.stream()
			.mapToLong(OrderProduct::getItemTotalPrice)
			.sum();
	}

	// 양방향 연관관계 메서드
	public void addOrderProduct(OrderProduct orderProduct) {
		orderProducts.add(orderProduct);
		orderProduct.initOrder(this);
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
