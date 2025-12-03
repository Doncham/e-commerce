package kr.hhplus.be.server.payment.request;

import kr.hhplus.be.server.order.Order;
import lombok.Getter;

@Getter
public class PayResponse {
	private String transactionId;
	private String status;
	private Long amount;
	private Long orderId;

	private PayResponse(String transactionId, String status, Long amount, Long orderId) {
		this.transactionId = transactionId;
		this.status = status;
		this.amount = amount;
		this.orderId = orderId;
	}

	public static PayResponse of(Order order, String transactionId){
		return new PayResponse(
			transactionId,
			order.getStatus().toString(),
			order.getPayAmount(),
			order.getId()
		);
	}
}
