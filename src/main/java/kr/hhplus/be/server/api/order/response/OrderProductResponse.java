package kr.hhplus.be.server.api.order.response;

import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import lombok.Builder;
import lombok.Getter;

@Getter
public class OrderProductResponse {
	private final Long orderProductId;
	private final Long productId;
	private final String productName;
	private final Long unitPrice;

	@Builder
	public OrderProductResponse(Long orderProductId, Long productId,
		String productName, Long unitPrice) {
		this.orderProductId = orderProductId;
		this.productId = productId;
		this.productName = productName;
		this.unitPrice = unitPrice;
	}
	public static OrderProductResponse from(OrderProduct op) {
		return OrderProductResponse.builder()
			.orderProductId(op.getId())
			.productId(op.getProductId())
			.productName(op.getProductNameSnap())
			.unitPrice(op.getUnitPrice())
			.build();
	}
}
