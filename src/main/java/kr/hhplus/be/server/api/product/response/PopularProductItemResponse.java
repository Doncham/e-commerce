package kr.hhplus.be.server.api.product.response;

import lombok.Getter;

@Getter
public class PopularProductItemResponse {
	private int rank;
	private long productId;
	private String name;
	private long price;
	private long soldQty;

	public PopularProductItemResponse(int rank, long productId, String name, long price, long soldQty) {
		this.rank = rank;
		this.productId = productId;
		this.name = name;
		this.price = price;
		this.soldQty = soldQty;
	}
}
