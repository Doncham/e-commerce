package kr.hhplus.be.server.api.payment.request;

import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.payment.PaymentPurpose;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentGatewayRequest {
	private PaymentPurpose purpose;
	private Long referenceId;
	private Long amount;
	private String idempotencyKey;

	private PaymentGatewayRequest(PaymentPurpose purpose, Long orderId, Long amount, String idempotencyKey) {
		this.purpose = purpose;
		this.referenceId = orderId;
		this.amount = amount;
		this.idempotencyKey = idempotencyKey;
	}

	public static PaymentGatewayRequest forOrderPayment(Order order, String idempotencyKey){
		return new PaymentGatewayRequest(
			PaymentPurpose.ORDER_PAYMENT,
			order.getId(),
			order.getPayAmount(),
			idempotencyKey
		);
	}

	public static PaymentGatewayRequest forPointCharge(Long chargeId, Long amount, String idempotencyKey) {
		return new PaymentGatewayRequest(
			PaymentPurpose.POINT_CHARGE,
			chargeId,
			amount,
			idempotencyKey
		);
	}
}
