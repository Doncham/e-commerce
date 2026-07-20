package kr.hhplus.be.server.domain.payment;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayType;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_payment_paid_at",
			columnNames = "paid_at"
		)
	}
)
public class Payment extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne
	@JoinColumn(name = "order_id", nullable = false, unique = true)
	private Order order;

	// pg에 실제로 요청한 금액
	@Column(nullable = false)
	private Long amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStatus status;

	@Column(nullable = true)
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

	private Payment(Order order, Long amount, PaymentStatus status, String idemKey, LocalDateTime paidAt, String pgTransactionId, PaymentGatewayType gatewayType) {
		this.order = order;
		this.amount = amount;
		this.status = status;
		this.idempotencyKey = idemKey;
		this.paidAt = paidAt;
		this.canceledAmount = 0L;
		this.gatewayType = gatewayType;
		this.pgTransactionId = pgTransactionId;
	}
	// 이렇게 간단하게 IdempotencyKey 만들어도 되나?
	public static Payment createPayment(Order order, Long amount, PaymentGatewayType gatewayType) {
		return new Payment(order, amount, PaymentStatus.REQUESTED, "orderId:" + order.getId(), null, null, gatewayType);
	}

	public void paymentSuccess(String pgTransactionId, LocalDateTime processedAt) {
		if(this.status != PaymentStatus.REQUESTED) {
			throw new IllegalArgumentException("paymentStatus가 REQUESTED가 아닙니다. paymentId = " + this.id);
		}
		this.pgTransactionId = pgTransactionId;
		this.status = PaymentStatus.SUCCESS;
		this.paidAt = processedAt;
	}

	public void paymentFailed(String reason) {
		if(this.status != PaymentStatus.REQUESTED) {
			throw new IllegalArgumentException("paymentStatus가 REQUESTED가 아닙니다. paymentId = " + this.id);
		}
		this.status = PaymentStatus.FAILURE;
		// 결제 실패니까 처리된게 아닌가? 규칙을 정하기 나름일듯
		paidAt = null;
		this.failReason = reason;
	}

	public boolean isFinalized() {
		return this.status != PaymentStatus.REQUESTED;
	}
	public boolean canStartCancel() {
		return this.status == PaymentStatus.SUCCESS || this.status == PaymentStatus.PARTIAL_CANCELED;
	}

	public void applyCancel(Long cancelAmount) {
		if (this.status != PaymentStatus.SUCCESS && this.status != PaymentStatus.PARTIAL_CANCELED) {
			throw new IllegalStateException("paymentStatus가 SUCCESS or PARTIAL_CANCELED가 아닙니다. paymentId = " + this.id);
		}

		if (cancelAmount <= 0) {
			throw new IllegalArgumentException("cancelAmount must be positive");
		}
		long nextCanceledAmount = this.canceledAmount + cancelAmount;
		if (nextCanceledAmount > this.amount) {
			throw new IllegalArgumentException("cancelAmount exceeds remaining amount");
		}

		this.canceledAmount = nextCanceledAmount;

		if (this.canceledAmount.equals(this.amount)) {
			cancel();
		} else {
			partialCancel();
		}
	}

	private void cancel() {
		this.status = PaymentStatus.CANCELLED;
	}

	private void partialCancel() {
		this.status = PaymentStatus.PARTIAL_CANCELED;
	}


}
