package kr.hhplus.be.server.domain.payment;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

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
		// 와 이거는 유니크 제약이 아니라 인덱스를 만들고 싶었던건데 엄청난 실수..
		// @UniqueConstraint(
		// 	name = "uk_payment_paid_at",
		// 	columnNames = "paid_at"
		// ),
		@UniqueConstraint(
			name = "ux_payment_pg_order_id",
			columnNames = "pg_order_id"
		),
		@UniqueConstraint(
			name = "ux_payment_payment_key",
			columnNames = "payment_key"
		),
		@UniqueConstraint(
			name = "ux_payment_idempotency_key",
			columnNames = "idempotency_key"
		),
		@UniqueConstraint(
			name = "ux_payment_order_id",
			columnNames = "order_id"
		)
	}
)
public class Payment extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	// pg에 실제로 요청한 금액
	@Column(nullable = false)
	private Long amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStatus status;

	// pg에 저장 + 승인 로직에서 payment 조회 역할
	@Column(name = "pg_order_id", nullable = false)
	private String pgOrderId;

	// 토스 인증 성공 후 저장
	@Column(name = "payment_key", unique = true)
	private String paymentKey;

	@Column(name = "paid_at")
	private LocalDateTime paidAt;

	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	@Column(name = "idempotency_key", nullable = false)
	private String idempotencyKey;

	private String failReason;

	@Column(nullable = false)
	private Long canceledAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentGatewayType gatewayType;

	private Payment(Order order, Long amount, PaymentStatus status, String idemKey, LocalDateTime paidAt, PaymentGatewayType gatewayType, String pgOrderId, LocalDateTime expiresAt) {
		this.order = order;
		this.amount = amount;
		this.status = status;
		this.idempotencyKey = idemKey;
		this.paidAt = paidAt;
		this.canceledAmount = 0L;
		this.gatewayType = gatewayType;
		this.pgOrderId = pgOrderId;
		this.expiresAt = expiresAt;
	}


	public static Payment createReady(Order order, Long amount, PaymentGatewayType gatewayType, String idempotencyKey, LocalDateTime expiresAt) {
		return new Payment(
			order,
			amount,
			PaymentStatus.READY,
			idempotencyKey,
			null,
			gatewayType,
			generatePgOrderId(),
			expiresAt
		);
	}

	private static String generatePgOrderId() {
		return "PAY_" + UUID.randomUUID();
	}


	public void paymentSuccess(LocalDateTime processedAt) {
		if (status == PaymentStatus.SUCCESS) {
			return;
		}

		if (status != PaymentStatus.CONFIRMING) {
			throw new IllegalStateException(
				"Payment cannot succeed. "
					+ "paymentId=" + id
					+ ", status=" + status
			);
		}

		this.status = PaymentStatus.SUCCESS;
		this.paidAt = processedAt;
	}

	public void paymentFailed(String reason) {
		if(this.status != PaymentStatus.READY) {
			throw new IllegalArgumentException("paymentStatus가 REQUESTED가 아닙니다. paymentId = " + this.id);
		}
		this.status = PaymentStatus.FAILED;
		// 결제 실패니까 처리된게 아닌가? 규칙을 정하기 나름일듯
		paidAt = null;
		this.failReason = reason;
	}

	public boolean isFinalized() {
		return this.status != PaymentStatus.READY;
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

	public boolean isConfirming() {
		return this.status.equals(PaymentStatus.CONFIRMING);
	}

	public boolean isReady() {
		return this.status.equals(PaymentStatus.READY);
	}

	public void invalidateForOrderChange() {
		// 멱등적
		if (status == PaymentStatus.INVALIDATED) {
			return;
		}

		// 규칙
		if (status == PaymentStatus.CONFIRMING) {
			throw new IllegalStateException(
				"Cannot invalidate confirming payment. "
					+ "paymentId=" + id
			);
		}

		if (status != PaymentStatus.READY) {
			throw new IllegalStateException(
				"Only READY payment can be invalidated. "
					+ "paymentId=" + id
					+ ", status=" + status
			);
		}

		this.status = PaymentStatus.INVALIDATED;
	}

	public void prepareAgain(
		long amount,
		String idempotencyKey,
		LocalDateTime expiresAt
	) {
		if (status == PaymentStatus.CONFIRMING
			|| status == PaymentStatus.SUCCESS
			|| status == PaymentStatus.PARTIAL_CANCELED
			|| status == PaymentStatus.CANCELLED) {

			throw new IllegalStateException(
				"Payment cannot be prepared again. "
					+ "paymentId=" + id
					+ ", status=" + status
			);
		}

		this.amount = amount;
		this.idempotencyKey = idempotencyKey;
		this.pgOrderId = generatePgOrderId();
		this.expiresAt = expiresAt;

		this.paymentKey = null;
		this.failReason = null;
		this.paidAt = null;

		this.status = PaymentStatus.READY;
	}

	public void startConfirm(
		String paymentKey
	) {
		if (status != PaymentStatus.READY) {
			throw new IllegalStateException(
				"Only READY payment can start confirmation. "
					+ "paymentId=" + id
					+ ", status=" + status
			);
		}

		this.paymentKey =
			Objects.requireNonNull(paymentKey);

		this.status =
			PaymentStatus.CONFIRMING;
	}
}
