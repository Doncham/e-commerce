package kr.hhplus.be.server.application.payment.dto;

import java.util.List;

import kr.hhplus.be.server.domain.payment.CancelType;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentCancelRequest {
	private Long paymentId;
	private String reason;
	private String idemKey;
	private Long userId;
	private List<Long> orderProductIds;

	@Builder
	public PaymentCancelRequest(Long paymentId, String reason, String idemKey, CancelType cancelType, Long userId,
		List<Long> orderProductIds) {
		this.paymentId = paymentId;
		this.reason = reason;
		this.idemKey = idemKey;
		this.userId = userId;
		this.orderProductIds = orderProductIds;
	}
}
