package kr.hhplus.be.server.application.payment.dto;

import kr.hhplus.be.server.api.payment.request.PaymentGatewayRequest;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.OrderStatus;
import lombok.Getter;

@Getter
public class PaymentAttempt {
	private final Long orderId;
	private final Long paymentId;
	private final Long amount;
	private final String idempotencyKey;
	private final OrderStatus orderStatus;

	private PaymentAttempt(Long orderId, Long paymentId, Long amount, String idempotencyKey, OrderStatus orderStatus) {
		this.orderId = orderId;
		this.paymentId = paymentId;
		this.amount = amount;
		this.idempotencyKey = idempotencyKey;
		this.orderStatus = orderStatus;
	}

	public static PaymentAttempt of(Long orderId, Long paymentId, Long amount, String idemKey, OrderStatus orderStatus){
		return new PaymentAttempt(orderId, paymentId, amount, idemKey, orderStatus);
	}

	public PaymentGatewayRequest toPgRequest() {
		return PaymentGatewayRequest.forOrderPayment(orderId, amount, idempotencyKey);
	}
}
