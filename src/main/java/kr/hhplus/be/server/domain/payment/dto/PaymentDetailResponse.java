package kr.hhplus.be.server.domain.payment.dto;

import java.time.LocalDateTime;
import java.util.List;

import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
public class PaymentDetailResponse {
	private final OrderStatus orderStatus;
	private final Long paymentId;
	private final PaymentStatus paymentStatus;
	private final Long orderId;
	private final Long paidAmount;
	private final Long cancelAmountTotal;
	private final Long cancelableAmount;
	private final LocalDateTime paidAt;
	private final String pgTransactionId;
	private final List<PaymentDetailItemResponse> orderProductDetails;

	@Builder
	private PaymentDetailResponse(
		OrderStatus orderStatus,
		Long paymentId,
		PaymentStatus paymentStatus,
		Long orderId,
		Long paidAmount,
		Long cancelAmountTotal,
		Long cancelableAmount,
		LocalDateTime paidAt,
		String pgTransactionId,
		List<PaymentDetailItemResponse> orderProductDetails
	) {
		this.orderStatus = orderStatus;
		this.paymentId = paymentId;
		this.paymentStatus = paymentStatus;
		this.orderId = orderId;
		this.paidAmount = paidAmount;
		this.cancelAmountTotal = cancelAmountTotal;
		this.cancelableAmount = cancelableAmount;
		this.paidAt = paidAt;
		this.pgTransactionId = pgTransactionId;
		this.orderProductDetails = orderProductDetails;
	}

	// payment 조회할 때 order fetch Join할거임.(orderProduct도)
	public static PaymentDetailResponse create(Payment payment) {

		return PaymentDetailResponse.builder()
			.orderStatus(payment.getOrder().getStatus())
			.paymentId(payment.getId())
			.paymentStatus(payment.getStatus())
			.orderId(payment.getOrder().getId())
			.paidAmount(payment.getAmount())
			.cancelAmountTotal(payment.getCanceledAmount())
			.cancelableAmount(payment.getAmount() - payment.getCanceledAmount())
			.paidAt(payment.getPaidAt())
			.pgTransactionId(payment.getPgTransactionId())
			.orderProductDetails(PaymentDetailItemResponse.create(payment.getOrder().getOrderProducts()))
			.build();
	}
}
