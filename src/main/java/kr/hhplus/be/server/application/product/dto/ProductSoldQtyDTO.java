package kr.hhplus.be.server.application.product.dto;

import lombok.Getter;

@Getter
public class ProductSoldQtyDTO {
	private long productId;
	private Long soldQty;

	public ProductSoldQtyDTO(long productId, long soldQty) {
		this.productId = productId;
		this.soldQty = soldQty;
	}
}
