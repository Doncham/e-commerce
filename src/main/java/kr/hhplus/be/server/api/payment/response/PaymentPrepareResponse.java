package kr.hhplus.be.server.api.payment.response;

import java.time.LocalDateTime;

import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentPrepareResponse {
	private Long orderId;
	private Long paymentId;
	private String pgOrderId;
	private PaymentStatus paymentStatus;
	private Long amount;

	@Builder
	public PaymentPrepareResponse(Long orderId, Long paymentId, String pgOrderId, PaymentStatus paymentStatus,
		Long amount) {
		this.orderId = orderId;
		this.paymentId = paymentId;
		this.pgOrderId = pgOrderId;
		this.paymentStatus = paymentStatus;
		this.amount = amount;
	}

	public static PaymentPrepareResponse from(
		Payment payment
	) {
		return PaymentPrepareResponse.builder()
			.orderId(payment.getOrder().getId())
			.paymentId(payment.getId())
			.pgOrderId(payment.getPgOrderId())
			.paymentStatus(payment.getStatus())
			.amount(payment.getAmount())
			.build();
	}
}
