package kr.hhplus.be.server.api.payment.request;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PayRequest {
	private String idempotencyKey;
	private Long orderId;

	@Builder
	public PayRequest(String idempotencyKey, Long orderId) {
		this.idempotencyKey = idempotencyKey;
		this.orderId = orderId;
	}
}
