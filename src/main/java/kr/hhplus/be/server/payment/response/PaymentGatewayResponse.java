package kr.hhplus.be.server.payment.response;

import kr.hhplus.be.server.payment.domain.PaymentGatewayStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentGatewayResponse {
	private String pgTransactionId;
	private PaymentGatewayStatus status;
	private Long paidAmount;

	private PaymentGatewayResponse(String pgTransactionId, PaymentGatewayStatus status, Long paidAmount) {
		this.pgTransactionId = pgTransactionId;
		this.status = status;
		this.paidAmount = paidAmount;
	}

	public static PaymentGatewayResponse of(String pgTransactionId, PaymentGatewayStatus status, Long paidAmount) {
		return new PaymentGatewayResponse(pgTransactionId, status, paidAmount);
	}
}
