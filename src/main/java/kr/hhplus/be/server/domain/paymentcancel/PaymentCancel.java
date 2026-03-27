package kr.hhplus.be.server.domain.paymentcancel;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.hhplus.be.server.domain.payment.CancelType;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(
	uniqueConstraints = {
		@UniqueConstraint(
		name = "ux_cancelFingerPrint",
		columnNames = {"cancel_finger_print"}
	),
		@UniqueConstraint(
			name = "ux_payment_cancel_pg_tx_id",
			columnNames = "pg_cancel_transaction_id")
	}
)
public class PaymentCancel {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(nullable = false)
	private Long paymentId;
	@Column(nullable = false, length = 100)
	private String idempotencyKey;
	@Column(nullable = false)
	private Long cancelAmount;
	@Column(nullable = false, length = 500)
	private String reason;
	@Column(length = 100)
	private String pgCancelTransactionId;

	private LocalDateTime canceledAt;

	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private PaymentCancelStatus status;
	@Column(nullable = false, length = 100)
	private String cancelFingerPrint;

	// 취소 실패 사유가 있네
	@Column(length = 500)
	private String failReason;

	protected PaymentCancel(Long payment, Long cancelAmount, String idempotencyKey, String reason, String cancelFingerPrint) {
		this.paymentId = payment;
		this.cancelAmount = cancelAmount;
		this.idempotencyKey = idempotencyKey;
		this.reason = reason;
		this.status = PaymentCancelStatus.REQUESTED;
		this.cancelFingerPrint = cancelFingerPrint;
	}
	public static PaymentCancel create(Long paymentId, Long cancelAmount, String idempotencyKey, String reason, String cancelFingerPrint) {
		return new PaymentCancel(paymentId, cancelAmount, idempotencyKey, reason, cancelFingerPrint);
	}

	public static String createFingerPrint(CancelType cancelType, Long paymentId,
		List<Long> orderProductIds) {
		if (cancelType == null) {
			throw new IllegalArgumentException("cancelType is required");
		}
		if (paymentId == null) {
			throw new IllegalArgumentException("paymentId is required");
		}
		if (orderProductIds == null || orderProductIds.isEmpty()) {
			throw new IllegalArgumentException("orderProductIds are required");
		}

		String joinedIds = orderProductIds.stream()
			.sorted()
			.map(String::valueOf)
			.reduce((a, b) -> a + "," + b)
			.orElseThrow();

		return cancelType + "|" + paymentId + "|" + joinedIds;
	}

	public void success(String pgCancelTransactionId, LocalDateTime canceledAt) {
		if (this.status != PaymentCancelStatus.PROCESSING) {
			throw new IllegalArgumentException("성공 처리할 수 없는 상태입니다. status= " + this.status);
		}
		if (pgCancelTransactionId == null || pgCancelTransactionId.isBlank()) {
			throw new IllegalArgumentException("pgCancelTransactionId is required");
		}
		if (canceledAt == null) {
			throw new IllegalArgumentException("canceledAt is required");
		}
		this.status = PaymentCancelStatus.SUCCEEDED;
		this.pgCancelTransactionId = pgCancelTransactionId;
		this.canceledAt = canceledAt;
	}
	public void failPermanent(String failReason) {
		if (this.status != PaymentCancelStatus.REQUESTED
			&& this.status != PaymentCancelStatus.PROCESSING) {
			throw new IllegalArgumentException("성공 처리할 수 없는 상태입니다. status= " + this.status);
		}
		this.status = PaymentCancelStatus.FAILED_PERMANENT;
		this.failReason = failReason;
	}
	public void failTemporary(String failReason) {
		if (this.status != PaymentCancelStatus.REQUESTED
			&& this.status != PaymentCancelStatus.PROCESSING) {
			throw new IllegalArgumentException("성공 처리할 수 없는 상태입니다. status= " + this.status);
		}
		this.status = PaymentCancelStatus.FAILED_RETRYABLE;
		this.failReason = failReason;
	}

	public void markProcessing() {
		if (this.status != PaymentCancelStatus.REQUESTED
			&& this.status != PaymentCancelStatus.FAILED_RETRYABLE) {
			throw new IllegalStateException("PROCESSING으로 변경할 수 없는 상태입니다. status=" + this.status);
		}
		this.status = PaymentCancelStatus.PROCESSING;
	}
}
