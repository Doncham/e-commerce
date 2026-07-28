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
import jakarta.validation.constraints.NotEmpty;
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
		name = "ux_userid_and_checkoutid",
		columnNames = {"user_id", "checkout_id"}
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
	private Long paymentAmount;
	private String memo;
	// 이것도 차감해줘야지
	@Column(nullable = false)
	private Long pointUsedTotal;
	@Column(name="checkout_id", nullable = false)
	@NotEmpty // "" , "  "도 막음.
	private String checkoutId;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderProduct> orderProducts = new ArrayList<>();

	public static Order createDraft(User user, String checkoutId) {
		return new Order(user, checkoutId);
	}
	private Order(User user, String checkoutId) {
		this.user = Objects.requireNonNull(user);
		this.checkoutId = Objects.requireNonNull(checkoutId);
		this.status = OrderStatus.DRAFT;
		this.itemTotal = 0L;
		this.couponDiscountTotal = 0L;
		this.paymentAmount = 0L;
		this.pointUsedTotal = 0L;
	}


	public void updateOrderDraft(
		List<OrderProduct> items,
		String memo,
		ShippingInfo shippingInfo
	) {
		ensureDraftState();
		// null, empty 체크
		validateItems(items);
		// update 전에 초기화
		this.orderProducts.clear();
		items.forEach(this::addOrderProduct);

		// item 총 가격 계산
		this.itemTotal = calculateItemTotal();
		this.couponDiscountTotal = 0L;
		this.pointUsedTotal = 0L;
		this.paymentAmount = itemTotal;
		this.memo = memo;
		this.shippingAddress = Objects.requireNonNull(shippingInfo, "shippingInfo is required");

	}
	// 결제 준비 메서드(초기 버전)
	public void preparePayment(
		long couponDiscountTotal,
		long pointUsedTotal
	) {
		ensureDraftState();

		if (couponDiscountTotal < 0 || pointUsedTotal < 0) {
			throw new IllegalArgumentException(
				"할인 금액은 음수일 수 없습니다."
			);
		}

		long calculatedPaymentAmount =
			itemTotal - couponDiscountTotal - pointUsedTotal;

		if (calculatedPaymentAmount < 0) {
			throw new IllegalArgumentException(
				"최종 결제 금액은 음수일 수 없습니다."
			);
		}

		if (calculateAllocatedPointTotal() != pointUsedTotal) {
			throw new IllegalStateException(
				"배분된 포인트 합계가 일치하지 않습니다."
			);
		}

		this.couponDiscountTotal = couponDiscountTotal;
		this.pointUsedTotal = pointUsedTotal;
		this.paymentAmount = calculatedPaymentAmount;
		this.status = OrderStatus.PAYMENT_PENDING;
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
	private void addOrderProduct(OrderProduct orderProduct) {
		orderProducts.add(orderProduct);
		orderProduct.initOrder(this);
	}

	public void paid() {
		if (status != OrderStatus.PAYMENT_PENDING) {
			throw new IllegalStateException(
				"PAYMENT_PENDING 상태 주문만 결제 완료할 수 있습니다."
			);
		}

		this.status = OrderStatus.PAID;
	}
	public void failed() {
		// 이거는 상태 검증 어떻게 해야할지 고민
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

	public void paymentPending() {
		if (this.status != OrderStatus.DRAFT) {
			throw new IllegalStateException("DRAFT 상태 주문만 결제를 준비할 수 있습니다.");
		}
		this.status = OrderStatus.PAYMENT_PENDING;
	}

	public boolean isPaymentPending() {
		return this.status == OrderStatus.PAYMENT_PENDING;
	}


	public boolean canStartPayment() {
		return this.status == OrderStatus.DRAFT;
	}

}
