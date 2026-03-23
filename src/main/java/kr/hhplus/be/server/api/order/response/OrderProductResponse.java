package kr.hhplus.be.server.api.order.response;

import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
public class OrderProductResponse {
	private final Long orderProductId;
	private final Long productId;
	private final String productName;
	private final Long unitPrice;
	private final Long appliedCouponId;
	private final Long allocatedCouponDiscount;
	private final Long allocatedPointUsed;
	private final Long netPaidAmount;
	@Builder
	public OrderProductResponse(Long orderProductId, Long productId, String productName, Long unitPrice, Long appliedCouponId,
		Long allocatedCouponDiscount, Long allocatedPointUsed, Long netPaidAmount) {
		this.orderProductId = orderProductId;
		this.productId = productId;
		this.productName = productName;
		this.unitPrice = unitPrice;
		this.appliedCouponId = appliedCouponId;
		this.allocatedCouponDiscount = allocatedCouponDiscount;
		this.allocatedPointUsed = allocatedPointUsed;
		this.netPaidAmount = netPaidAmount;
	}
	public static OrderProductResponse from(OrderProduct op){
		return OrderProductResponse.builder()
			.orderProductId(op.getId())
			.productId(op.getProductId())
			.productName(op.getProductNameSnap())
			.appliedCouponId(op.getAppliedCouponId())
			.unitPrice(op.getUnitPrice())
			.allocatedCouponDiscount(op.getAllocatedCouponDiscount())
			.allocatedPointUsed(op.getAllocatedPointUsed())
			.netPaidAmount(op.getNetPaidAmount())
			.build();
	}
}
