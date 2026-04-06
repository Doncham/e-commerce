package kr.hhplus.be.server.application.payment.pg;

import java.time.LocalDateTime;

import kr.hhplus.be.server.domain.payment.PaymentGatewayStatus;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentGatewayCancelResponse {
	private String pgCancelTransactionId;
	private PaymentGatewayStatus status; // SUCCESS / FAILURE
	private Long canceledAmount;
	private String failureCode;
	private String failureMessage;
	private LocalDateTime canceledAt;
	@Builder
	public PaymentGatewayCancelResponse(String pgCancelTransactionId, PaymentGatewayStatus status, Long canceledAmount,
		String failureCode, String failureMessage, LocalDateTime canceledAt) {
		this.pgCancelTransactionId = pgCancelTransactionId;
		this.status = status;
		this.canceledAmount = canceledAmount;
		this.failureCode = failureCode;
		this.failureMessage = failureMessage;
		this.canceledAt = canceledAt;
	}
}
