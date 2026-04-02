package kr.hhplus.be.server.domain.paymentcancel;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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

	@Column(nullable = false, length = 255)
	private String cancelFingerPrint;

	@Column(nullable = false)
	private String orderProductSnapshot;

	@Column(nullable = false)
	private boolean pgCancelCompleted;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentCancelFailurePhase failurePhase;

	@Column(length = 500)
	private String failReason;

	protected PaymentCancel(
		Long paymentId,
		Long cancelAmount,
		String idempotencyKey,
		String reason,
		String cancelFingerPrint,
		String snapshot
	) {
		this.paymentId = paymentId;
		this.cancelAmount = cancelAmount;
		this.idempotencyKey = idempotencyKey;
		this.reason = reason;
		this.status = PaymentCancelStatus.REQUESTED;
		this.cancelFingerPrint = cancelFingerPrint;
		this.failurePhase = PaymentCancelFailurePhase.BEFORE_PG;
		this.orderProductSnapshot = snapshot;
		this.pgCancelCompleted = false;
	}

	public static PaymentCancel create(
		Long paymentId,
		Long cancelAmount,
		String idempotencyKey,
		String reason,
		String cancelFingerPrint,
		String snapshot
	) {
		return new PaymentCancel(paymentId, cancelAmount, idempotencyKey, reason, cancelFingerPrint, snapshot);
	}

	public static String createFingerprint(Long paymentId, List<Long> orderProductIds) {
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

		return "CANCEL|" + paymentId + "|" + joinedIds;
	}

	public void markProcessing() {
		if (status != PaymentCancelStatus.REQUESTED
			&& status != PaymentCancelStatus.FAILED_RETRYABLE) {
			throw new IllegalStateException("PROCESSING으로 변경할 수 없는 상태입니다. status=" + status);
		}
		this.status = PaymentCancelStatus.PROCESSING;
	}

	public void markPgCanceledCompleted(String pgCancelTxId, LocalDateTime canceledAt) {
		if (status != PaymentCancelStatus.PROCESSING) {
			throw new IllegalStateException("PG 취소 완료를 기록할 수 없는 상태입니다. status=" + status);
		}
		this.pgCancelTransactionId = requireText(pgCancelTxId, "pgCancelTxId");
		this.canceledAt = Objects.requireNonNull(canceledAt, "canceledAt");
		this.pgCancelCompleted = true;
		this.failurePhase = PaymentCancelFailurePhase.AFTER_PG;
		this.failReason = null;
	}

	public void failRetryableBeforePg(String failReason) {
		if (pgCancelCompleted) {
			throw new IllegalStateException("이미 PG 취소 완료된 작업은 BEFORE_PG 실패가 될 수 없습니다.");
		}
		failRetryable(PaymentCancelFailurePhase.BEFORE_PG, failReason);
	}

	public void failRetryableAfterPg(String failReason) {
		if (!pgCancelCompleted) {
			throw new IllegalStateException("PG 취소 전 작업은 AFTER_PG 실패가 될 수 없습니다.");
		}
		failRetryable(PaymentCancelFailurePhase.AFTER_PG, failReason);
	}

	public void failPermanent(String failReason) {
		if (!isFailTransitionAllowed()) {
			throw new IllegalStateException("영구 실패로 변경할 수 없는 상태입니다. status=" + status);
		}
		this.status = PaymentCancelStatus.FAILED_PERMANENT;
		this.failReason = requireText(failReason, "failReason");
	}

	public void success() {
		if (status != PaymentCancelStatus.PROCESSING) {
			throw new IllegalStateException("완료 처리할 수 없는 상태입니다. status=" + status);
		}
		this.status = PaymentCancelStatus.SUCCEEDED;
		this.failReason = null;
	}

	public List<Long> getTargetOrderProductIds() {
		if (orderProductSnapshot == null || orderProductSnapshot.isBlank()) {
			throw new IllegalStateException("orderProductSnapshot is empty");
		}

		return Arrays.stream(orderProductSnapshot.split(","))
			.map(String::trim)
			.filter(s -> !s.isBlank())
			.map(Long::valueOf)
			.toList();
	}

	private void failRetryable(PaymentCancelFailurePhase phase, String failReason) {
		if (!isFailTransitionAllowed()) {
			throw new IllegalStateException("재시도 가능 실패로 변경할 수 없는 상태입니다. status=" + status);
		}
		this.status = PaymentCancelStatus.FAILED_RETRYABLE;
		this.failurePhase = phase;
		this.failReason = requireText(failReason, "failReason");
	}

	private boolean isFailTransitionAllowed() {
		return status == PaymentCancelStatus.REQUESTED
			|| status == PaymentCancelStatus.PROCESSING;
	}

	private static String requireText(String value, String fieldName) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(fieldName + " is required");
		}
		return value;
	}
}
