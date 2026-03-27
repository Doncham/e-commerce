package kr.hhplus.be.server.application.payment.dto;

import kr.hhplus.be.server.domain.payment.CancelType;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentFullCancelRequest {
	private Long paymentId;
	private String reason;
	private String idemKey;

	@Builder
	public PaymentFullCancelRequest(Long paymentId, String reason, String idemKey, CancelType cancelType) {
		this.paymentId = paymentId;
		this.reason = reason;
		this.idemKey = idemKey;
	}
}
