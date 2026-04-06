package kr.hhplus.be.server.api.payment.response;

import java.time.LocalDateTime;

import kr.hhplus.be.server.domain.payment.PaymentGatewayStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentGatewayResponse {
	private String pgTransactionId;
	private PaymentGatewayStatus status;
	private Long paidAmount;
	private LocalDateTime approvedAt;
	private String errorCode;
	private String errorMessage;

	private PaymentGatewayResponse(String pgTransactionId, PaymentGatewayStatus status, Long paidAmount, LocalDateTime approvedAt, String errorCode, String errorMessage) {
		this.pgTransactionId = pgTransactionId;
		this.status = status;
		this.paidAmount = paidAmount;
		this.approvedAt = approvedAt;
		this.errorCode = errorCode;
		this.errorMessage = errorMessage;
	}

	public static PaymentGatewayResponse of(String pgTransactionId, PaymentGatewayStatus status, Long paidAmount, String errorCode, String errorMessage) {
		return new PaymentGatewayResponse(pgTransactionId, status, paidAmount, LocalDateTime.now(),errorCode, errorMessage);
	}
}
