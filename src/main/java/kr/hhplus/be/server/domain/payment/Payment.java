package kr.hhplus.be.server.domain.payment;

import java.time.LocalDateTime;

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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayType;
import kr.hhplus.be.server.entity.BaseTimeEntity;
import kr.hhplus.be.server.domain.order.Order;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
	uniqueConstraints = @UniqueConstraint(
		name = "ux_orderId_and_idempotencyKey",
		columnNames = {"order_id", "idempotency_key"}
	)
)
public class Payment extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 한 주문에 대해서 결제가 여러번 생길 수도 있을듯
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	// pg에 실제로 요청한 금액
	@Column(nullable = false)
	private Long amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStatus status;

	@Column(nullable = true, unique = true)
	private String pgTransactionId;

	private LocalDateTime paidAt;
	@Column(name = "idempotency_key", nullable = false)
	private String idempotencyKey;

	private String failReason;
	@Column(nullable = false)
	private Long canceledAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentGatewayType gatewayType;

	private Payment(Order order, Long amount, PaymentStatus status, String idemKey, String pgTransactionId, LocalDateTime paidAt, PaymentGatewayType gatewayType) {
		this.order = order;
		this.amount = amount;
		this.status = status;
		this.idempotencyKey = idemKey;
		this.pgTransactionId = pgTransactionId;
		this.paidAt = paidAt;
		this.canceledAmount = 0L;
		this.gatewayType = gatewayType;
	}
	public static Payment createPayment(Order order, String idemKey, Long amount, PaymentGatewayType gatewayType) {
		return new Payment(order, amount, PaymentStatus.REQUESTED, idemKey,null, null, gatewayType);
	}

	public void paymentSuccess(String pgTransactionId, LocalDateTime processedAt) {
		this.status = PaymentStatus.SUCCESS;
		this.pgTransactionId = pgTransactionId;
		this.paidAt = processedAt;
	}

	public void paymentFailed(String pgTransactionId, String reason) {
		this.pgTransactionId = pgTransactionId;
		this.status = PaymentStatus.FAILURE;
		// 결제 실패니까 처리된게 아닌가? 규칙을 정하기 나름일듯
		paidAt = null;
		this.failReason = reason;
	}

	public boolean isFinalized() {
		return this.status != PaymentStatus.REQUESTED;
	}

	public void cancelAmount(Long cancelAmount) {
		if (cancelAmount <= 0) {
			throw new IllegalArgumentException("cancelAmount must be positive");
		}
		long nextCanceledAmount = this.canceledAmount + cancelAmount;
		if (nextCanceledAmount > this.amount) {
			throw new IllegalArgumentException("cancelAmount exceeds remaining amount");
		}
		this.canceledAmount = nextCanceledAmount;

		if (this.canceledAmount.equals(this.amount)) {
			this.status = PaymentStatus.CANCELLED;
		} else {
			this.status = PaymentStatus.PARTIAL_CANCELED;
		}
	}

}
