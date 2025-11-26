package kr.hhplus.be.server.order.response;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ItemResponse {
	private Long productId;
	private String productName;
	private int qty;
	private Long unitPrice;
	@Builder
	public ItemResponse(Long productId, String productName, int qty, Long unitPrice) {
		this.productId = productId;
		this.productName = productName;
		this.qty = qty;
		this.unitPrice = unitPrice;
	}
}
