package kr.hhplus.be.server.domain.payment.dto;

import kr.hhplus.be.server.application.payment.pg.PaymentGatewayType;
import kr.hhplus.be.server.domain.paymentcancel.PaymentCancel;
import kr.hhplus.be.server.domain.paymentcancel.PaymentCancelStatus;
import lombok.Getter;

@Getter
public class PaymentCancelJob {
	private final Long paymentCancelId;
	private final Long paymentId;
	private final String merchantOrderId;
	private final String cancelIdempotencyKey;
	private final Long cancelAmount;
	private final String reason;
	private final PaymentGatewayType gatewayType;
	private final String originalPgTransactionId;
	private final boolean pgCancelCompleted;
	private final String failReason;
	private final PaymentCancelStatus status;

	private PaymentCancelJob(
		Long paymentCancelId,
		Long paymentId,
		String merchantOrderId,
		String cancelIdempotencyKey,
		Long cancelAmount,
		String reason,
		PaymentGatewayType gatewayType,
		String originalPgTransactionId,
		boolean pgCancelCompleted,
		String failReason,
		PaymentCancelStatus status
	) {
		this.paymentCancelId = paymentCancelId;
		this.paymentId = paymentId;
		this.merchantOrderId = merchantOrderId;
		this.cancelIdempotencyKey = cancelIdempotencyKey;
		this.cancelAmount = cancelAmount;
		this.reason = reason;
		this.gatewayType = gatewayType;
		this.originalPgTransactionId = originalPgTransactionId;
		this.pgCancelCompleted = pgCancelCompleted;
		this.failReason = failReason;
		this.status = status;
	}

	public static PaymentCancelJob of(
		PaymentCancel paymentCancel,
		PaymentGatewayType gatewayType,
		String originalPgTransactionId,
		String merchantOrderId
	) {
		return new PaymentCancelJob(
			paymentCancel.getId(),
			paymentCancel.getPaymentId(),
			merchantOrderId,
			paymentCancel.getIdempotencyKey(),
			paymentCancel.getCancelAmount(),
			paymentCancel.getReason(),
			gatewayType,
			originalPgTransactionId,
			paymentCancel.isPgCancelCompleted(),
			paymentCancel.getFailReason(),
			paymentCancel.getStatus()
		);
	}

	public boolean isSucceeded() {
		return status == PaymentCancelStatus.SUCCEEDED;
	}

	public boolean isProcessing() {
		return status == PaymentCancelStatus.PROCESSING;
	}

	public boolean isPermanentFailed() {
		return status == PaymentCancelStatus.FAILED_PERMANENT;
	}

	public boolean isRetryable() {
		return status == PaymentCancelStatus.REQUESTED
			|| status == PaymentCancelStatus.FAILED_RETRYABLE;
	}
}
