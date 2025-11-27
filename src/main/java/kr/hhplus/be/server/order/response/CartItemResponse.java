package kr.hhplus.be.server.order.response;

import kr.hhplus.be.server.orderproduct.OrderProduct;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class CartItemResponse {
	private Long productId;
	private String productName;
	private int qty;
	private Long unitPrice;
	@Builder
	public CartItemResponse(Long productId, String productName, int qty, Long unitPrice) {
		this.productId = productId;
		this.productName = productName;
		this.qty = qty;
		this.unitPrice = unitPrice;
	}
	public static CartItemResponse from(OrderProduct op){
		return CartItemResponse.builder()
			.productId(op.getProductId())
			.productName(op.getProductNameSnap())
			.qty(op.getQty())
			.unitPrice(op.getUnitPrice())
			.build();
	}
}
