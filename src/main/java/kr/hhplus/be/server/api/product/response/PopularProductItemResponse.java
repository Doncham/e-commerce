package kr.hhplus.be.server.api.product.response;

import kr.hhplus.be.server.application.product.PopularProductRowWithRank;
import lombok.Getter;

@Getter
public class PopularProductItemResponse {
	private int rank;
	private long productId;
	private String name;
	private long price;
	private long totalSoldCount;

	public PopularProductItemResponse(int rank, long productId, String name, long price, long totalSoldCount) {
		this.rank = rank;
		this.productId = productId;
		this.name = name;
		this.price = price;
		this.totalSoldCount = totalSoldCount;
	}
	public static PopularProductItemResponse from(PopularProductRowWithRank product) {
		return new PopularProductItemResponse(
			product.getRank(),
			product.getProductId(),
			product.getProductName(),
			product.getPrice(),
			product.getTotalSoldCount()
		);
	}
}
