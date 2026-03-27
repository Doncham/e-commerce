package kr.hhplus.be.server.application.payment.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentPartialCancelRequest {
	private List<Long> orderProductIds;
	private Long paymentId;
	private String reason;
	private String idemKey;
	@Builder
	public PaymentPartialCancelRequest(List<Long> orderProductIds, Long paymentId, String reason, String idemKey) {
		this.orderProductIds = orderProductIds;
		this.paymentId = paymentId;
		this.reason = reason;
		this.idemKey = idemKey;
	}
}
