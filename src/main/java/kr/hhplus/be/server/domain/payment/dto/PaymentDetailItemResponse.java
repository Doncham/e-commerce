package kr.hhplus.be.server.domain.payment.dto;

import java.time.LocalDateTime;
import java.util.List;

import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.domain.orderproduct.OrderProductStatus;
import kr.hhplus.be.server.domain.paymentcancel.PaymentCancel;
import lombok.Builder;
import lombok.Getter;

@Getter
public class PaymentDetailItemResponse {
	private final Long orderProductId;
	private final Long productId;
	private final String productNameSnap;
	private final Long unitPrice;
	private final OrderProductStatus orderProductStatus;
	private final Long allocatedCouponDiscount;
	private final Long allocatedPointUsed;
	private final Long netPaidAmount;
	private final LocalDateTime canceledAt;
	private final String cancelReason;

	@Builder
	private PaymentDetailItemResponse(
		Long orderProductId,
		Long productId,
		String productNameSnap,
		Long unitPrice,
		OrderProductStatus orderProductStatus,
		Long allocatedCouponDiscount,
		Long allocatedPointUsed,
		Long netPaidAmount,
		LocalDateTime canceledAt,
		String cancelReason
	) {
		this.orderProductId = orderProductId;
		this.productId = productId;
		this.productNameSnap = productNameSnap;
		this.unitPrice = unitPrice;
		this.orderProductStatus = orderProductStatus;
		this.allocatedCouponDiscount = allocatedCouponDiscount;
		this.allocatedPointUsed = allocatedPointUsed;
		this.netPaidAmount = netPaidAmount;
		this.canceledAt = canceledAt;
		this.cancelReason = cancelReason;
	}

	public static List<PaymentDetailItemResponse> create(List<OrderProduct> orderProducts) {
		return orderProducts.stream()
			.map(PaymentDetailItemResponse::of)
			.toList();
	}

	public static PaymentDetailItemResponse of(OrderProduct op) {
		PaymentCancel paymentCancel = op.getPaymentCancel();
		return PaymentDetailItemResponse.builder()
			.orderProductId(op.getId())
			.productId(op.getProductId())
			.productNameSnap(op.getProductNameSnap())
			.unitPrice(op.getUnitPrice())
			.orderProductStatus(op.getStatus())
			.allocatedCouponDiscount(op.getAllocatedCouponDiscount())
			.allocatedPointUsed(op.getAllocatedPointUsed())
			.netPaidAmount(op.getNetPaidAmount())
			.canceledAt(paymentCancel != null ? paymentCancel.getCanceledAt() : null)
			.cancelReason(paymentCancel != null ? paymentCancel.getReason() : null)
			.build();
	}
}
