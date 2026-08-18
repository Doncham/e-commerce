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
import jakarta.validation.constraints.NotBlank;
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
		name = "ux_userid_and_ordersessionid",
		columnNames = {
			"user_id",
			"order_session_id"
		}
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
	@Column(name="order_session_id", nullable = false)
	//@NotEmpty // "" 막음 " "는 못 막음.
	@NotBlank // " " 막음
	private String orderSessionId;

	@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
	private List<OrderProduct> orderProducts = new ArrayList<>();

	public static Order createDraft(User user, String orderSessionId) {
		return new Order(user, orderSessionId);
	}
	private Order(User user, String orderSessionId) {
		this.user = Objects.requireNonNull(user);
		this.orderSessionId = Objects.requireNonNull(orderSessionId);
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

	public void applyPaymentPreparation(
		long couponDiscountTotal,
		long pointUsedTotal,
		long paymentAmount
	) {
		ensureDraftState();

		if (couponDiscountTotal < 0
			|| pointUsedTotal < 0
			|| paymentAmount < 0) {

			throw new IllegalArgumentException(
				"Payment amounts cannot be negative."
			);
		}

		this.couponDiscountTotal =
			couponDiscountTotal;

		this.pointUsedTotal =
			pointUsedTotal;

		this.paymentAmount =
			paymentAmount;

		this.status =
			OrderStatus.PAYMENT_PENDING;
	}

	public boolean isPaymentPending() {
		return this.status == OrderStatus.PAYMENT_PENDING;
	}


	public boolean canStartPayment() {
		return this.status == OrderStatus.DRAFT;
	}

	public boolean isDraft() {
		return this.status.equals(OrderStatus.DRAFT);
	}

	public void reopenDraft() {
		// 이거 그냥 바꾸면 되나? FAILED나 SUCCESS 같은 상태면 안됨
		// PAYMENT_PENDING 이면서 payment의 상태도 READY인 경우만 허용
		if(this.status.equals(OrderStatus.PAYMENT_PENDING)){
			this.status = OrderStatus.DRAFT;
		}
	}
}
