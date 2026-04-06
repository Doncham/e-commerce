package kr.hhplus.be.server.application.payment.pg;

import kr.hhplus.be.server.domain.payment.dto.PaymentCancelJob;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentGatewayCancelRequest {
	private String paymentKey;            // PG 원결제 식별자
	private String merchantOrderId;       // 우리 주문 식별자
	private String cancelIdempotencyKey;  // 외부 멱등 키
	private Long cancelAmount;
	private String reason;
	private boolean fullCancel;

	@Builder
	public PaymentGatewayCancelRequest(
		String paymentKey,
		String merchantOrderId,
		String cancelIdempotencyKey,
		Long cancelAmount,
		String reason,
		boolean fullCancel
	) {
		this.paymentKey = paymentKey;
		this.merchantOrderId = merchantOrderId;
		this.cancelIdempotencyKey = cancelIdempotencyKey;
		this.cancelAmount = cancelAmount;
		this.reason = reason;
		this.fullCancel = fullCancel;
	}

	public static PaymentGatewayCancelRequest from(PaymentCancelJob job) {
		return PaymentGatewayCancelRequest.builder()
			.paymentKey(job.getOriginalPgTransactionId())
			.merchantOrderId(job.getMerchantOrderId())
			.cancelIdempotencyKey(job.getCancelIdempotencyKey())
			.cancelAmount(job.getCancelAmount())
			.reason(job.getReason())
			.build();
	}
}
