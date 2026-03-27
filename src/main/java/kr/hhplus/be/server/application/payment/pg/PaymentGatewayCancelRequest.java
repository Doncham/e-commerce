package kr.hhplus.be.server.application.payment.pg;

import kr.hhplus.be.server.domain.payment.CancelType;
import kr.hhplus.be.server.application.payment.dto.CancelCommand;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentGatewayCancelRequest {
	private String paymentKey; // PG 쪽 원결제 식별자
	private String merchantOrderId; // 우리 주문 식별자 현재: orderId, 미래: ORD-20260326-000123 이런 식별자
	private String cancelIdempotencyKey;
	private Long cancelAmount;
	private String reason;
	private boolean fullCancel;
	@Builder
	public PaymentGatewayCancelRequest(String paymentKey, String merchantOrderId, String cancelIdempotencyKey,
		Long cancelAmount, String reason, boolean fullCancel) {
		this.paymentKey = paymentKey;
		this.merchantOrderId = merchantOrderId;
		this.cancelIdempotencyKey = cancelIdempotencyKey;
		this.cancelAmount = cancelAmount;
		this.reason = reason;
		this.fullCancel = fullCancel;
	}

	public static PaymentGatewayCancelRequest from(CancelCommand command) {
		return PaymentGatewayCancelRequest.builder()
			.paymentKey(command.getPgTransactionId())
			.merchantOrderId(String.valueOf(command.getOrderId()))
			.cancelAmount(command.getCancelAmount())
			.cancelIdempotencyKey(command.getIdempotencyKey())
			.reason(command.getReason())
			.fullCancel(command.getCancelType() == CancelType.FULL)
			.build();
	}
}
