package kr.hhplus.be.server.payment.request;

import kr.hhplus.be.server.order.Order;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentGatewayRequest {
	private Long orderId;
	private Long amount;

	private PaymentGatewayRequest(Long orderId, Long amount) {
		this.orderId = orderId;
		this.amount = amount;
	}

	public static PaymentGatewayRequest of(Order order) {
		return new PaymentGatewayRequest(order.getId(), order.getPayAmount());
	}

}
