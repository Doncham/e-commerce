package kr.hhplus.be.server.api.payment.request;

import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentStatus;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PayResponse {
	private String transactionId;
	private Long amount;
	private Long orderId;
	private PaymentStatus status;
	private String failReason;
	private String message;

	@Builder
	private PayResponse(String transactionId, Long amount, Long orderId, PaymentStatus status, String failReason, String message) {
		this.transactionId = transactionId;
		this.amount = amount;
		this.orderId = orderId;
		this.status = status;
		this.failReason = failReason;
		this.message = message;
	}

	public static PayResponse of(Order order, Payment payment){
		String message = extractMessage(payment);
		return PayResponse.builder()
			.orderId(order.getId())
			.amount(order.getPayAmount())
			.transactionId(payment.getPgTransactionId())
			.status(payment.getStatus())
			.failReason(payment.getFailReason())
			.message(message)
			.build();
	}

	public static String extractMessage(Payment payment) {
		if(payment.getStatus() == PaymentStatus.REQUESTED) {
			return "결제 처리중";
		}
		else if(payment.getStatus() == PaymentStatus.SUCCESS) {
			return "결제 성공";
		} else  if (payment.getStatus() == PaymentStatus.FAILURE) {
			return "결제 실패";
		} else {
			return "결제 취소";
		}
	}
}
