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
import jakarta.persistence.Index;
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
	name = "orders",
	uniqueConstraints = @UniqueConstraint(
		name = "ux_userid_and_idempotencyKey",
		columnNames = {"user_id", "idempotency_key"}
	),
	indexes = {
		@Index(name = "ix_orders_created_at", columnList = "created_at")
	}
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

	@Column(nullable = false)
	private Long couponDiscountTotal;
	@Column(nullable = false)
	private Long payAmount;
	private String memo;
	// 이것도 차감해줘야지
	@Column(nullable = false)
	private Long pointUsedTotal;
	@Column(name="idempotency_key", nullable = false)
	private String idempotencyKey;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderProduct> orderProducts;

	public static Order createDraft(User user, ShippingInfo shippingInfo, String idempotencyKey) {
		return new Order(user, shippingInfo, idempotencyKey);
	}
	private Order(User user, ShippingInfo shippingInfo, String idempotencyKey) {
		this.user = Objects.requireNonNull(user);
		this.shippingAddress = Objects.requireNonNull(shippingInfo);
		this.idempotencyKey = Objects.requireNonNull(idempotencyKey);
		this.status = OrderStatus.DRAFT;
		this.itemTotal = 0L;
		this.couponDiscountTotal = 0L;
		this.payAmount = 0L;
		this.pointUsedTotal = 0L;
		this.orderProducts = new ArrayList<>();
	}


	public void completeOrderDraft(
		List<OrderProduct> items,
		Long couponDiscountTotal,
		String memo,
		Long pointUsedTotal
	) {
		ensureDraftState();
		// null, empty 체크
		validateItems(items);
		items.forEach(this::addOrderProduct);

		// item 총 가격 계산
		this.itemTotal = calculateItemTotal();
		this.couponDiscountTotal = couponDiscountTotal == null ? 0L : couponDiscountTotal;
		this.pointUsedTotal = pointUsedTotal == null ? 0L : pointUsedTotal;
		long payable = this.itemTotal - this.couponDiscountTotal - this.pointUsedTotal;
		if (payable < 0) {
			throw new IllegalStateException("payAmount cannot be negative");
		}
		this.payAmount = payable;

		// 포인트 할당 검증
		long allocatedPointTotal = calculateAllocatedPointTotal();
		if (allocatedPointTotal != this.pointUsedTotal) {
			throw new IllegalStateException("allocated pointUsedTotal sum mismatch");
		}

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
			.mapToLong(OrderProduct::getUnitPrice)
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
	public boolean canCancelAnyProduct() {
		return this.status == OrderStatus.PAID || this.status == OrderStatus.PARTIAL_CANCELED;
	}

	private long calculateAllocatedPointTotal() {
		return this.orderProducts.stream()
			.mapToLong(OrderProduct::getAllocatedPointUsed)
			.sum();
	}
	public void applyCancelResult(boolean fullyCanceled) {
		if (this.status != OrderStatus.PAID && this.status != OrderStatus.PARTIAL_CANCELED) {
			throw new IllegalStateException("취소 결과를 반영할 수 없는 주문 상태입니다. status=" + this.status);
		}

		if (fullyCanceled) {
			this.status = OrderStatus.CANCELED;
		} else {
			this.status = OrderStatus.PARTIAL_CANCELED;
		}
	}
}
