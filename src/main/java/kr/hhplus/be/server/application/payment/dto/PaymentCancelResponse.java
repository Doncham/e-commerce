package kr.hhplus.be.server.application.payment.dto;

import java.time.LocalDateTime;
import java.util.List;

import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.paymentcancel.PaymentCancel;
import lombok.Builder;
import lombok.Getter;

@Getter
public class PaymentCancelResponse {
	private final Long paymentCancelId;
	private final Long paymentId;
	private final Long orderId;

	private final String paymentStatus;
	private final String orderStatus;

	private final Long totalCanceledAmount;  // 이번 취소 금액
	private final Long currentCancelAmount;  // 누적 취소 금액
	private final Long remainingCancelableAmount;  // 남은 취소 가능 금액

	private final boolean fullCancel;
	private final String pgCancelTransactionId;
	private final LocalDateTime canceledAt;

	private final List<CanceledOrderProductResponse> canceledOrderProducts;
	@Builder
	public PaymentCancelResponse(Long paymentCancelId, Long paymentId, Long orderId, String paymentStatus,
		String orderStatus, Long totalCanceledAmount, boolean fullCancel, String pgCancelTransactionId,
		LocalDateTime canceledAt, List<CanceledOrderProductResponse> canceledOrderProducts,
		Long remainingCancelableAmount) {
		this.paymentCancelId = paymentCancelId;
		this.paymentId = paymentId;
		this.orderId = orderId;
		this.paymentStatus = paymentStatus;
		this.orderStatus = orderStatus;
		this.totalCanceledAmount = totalCanceledAmount;
		this.currentCancelAmount = canceledOrderProducts.stream()
			.mapToLong(CanceledOrderProductResponse::getRefundedAmount)
			.sum();
		this.fullCancel = fullCancel;
		this.pgCancelTransactionId = pgCancelTransactionId;
		this.canceledAt = canceledAt;
		this.canceledOrderProducts = canceledOrderProducts;
		this.remainingCancelableAmount = remainingCancelableAmount;
	}

	public static PaymentCancelResponse from(Payment payment,
		PaymentCancel paymentCancel,
		Order order,
		List<OrderProduct> orderProducts,
		boolean fullCancel
	) {
		List<CanceledOrderProductResponse> orderProductResponses = orderProducts.stream()
			.map(CanceledOrderProductResponse::from)
			.toList();
		return PaymentCancelResponse.builder()
			.paymentCancelId(paymentCancel.getId())
			.paymentId(payment.getId())
			.orderId(order.getId())
			.paymentStatus(payment.getStatus().toString())
			.orderStatus(order.getStatus().toString())
			.totalCanceledAmount(payment.getCanceledAmount())
			.remainingCancelableAmount(payment.getAmount() - payment.getCanceledAmount())
			.fullCancel(fullCancel)
			.pgCancelTransactionId(paymentCancel.getPgCancellationId())
			.canceledAt(paymentCancel.getCanceledAt())
			.canceledOrderProducts(orderProductResponses)
			.build();
	}

	@Getter
	public static class CanceledOrderProductResponse {
		private final Long orderProductId;
		private final Long productId;
		private final Long refundedAmount;
		private final Long restoredPointAmount;

		@Builder
		public CanceledOrderProductResponse(Long orderProductId, Long productId, Long refundedAmount,
			Long restoredPointAmount) {
			this.orderProductId = orderProductId;
			this.productId = productId;
			this.refundedAmount = refundedAmount;
			this.restoredPointAmount = restoredPointAmount;
		}

		public static CanceledOrderProductResponse from(OrderProduct orderProduct) {
			return CanceledOrderProductResponse.builder()
				.orderProductId(orderProduct.getId())
				.productId(orderProduct.getProductId())
				.refundedAmount(orderProduct.getCancelableAmount())
				.restoredPointAmount(orderProduct.getAllocatedPointUsed())
				.build();
		}
	}



}
