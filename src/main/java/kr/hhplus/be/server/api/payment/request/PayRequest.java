package kr.hhplus.be.server.api.payment.request;

import kr.hhplus.be.server.application.payment.pg.PaymentGatewayType;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PayRequest {
	private String idempotencyKey;
	private Long orderId;
	private PaymentGatewayType gatewayType;

	private PayRequest(String idempotencyKey, Long orderId, PaymentGatewayType gatewayType) {
		this.idempotencyKey = idempotencyKey;
		this.orderId = orderId;
		this.gatewayType = gatewayType;
	}

	public static PayRequest of(String idempotencyKey, Long orderId, PaymentGatewayType gatewayType) {
		return new PayRequest(idempotencyKey, orderId, gatewayType);
	}
}
