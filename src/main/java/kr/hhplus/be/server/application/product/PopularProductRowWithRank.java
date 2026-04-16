package kr.hhplus.be.server.application.product;

import lombok.Getter;

@Getter
public class PopularProductRowWithRank {
	public Integer rank;
	public Long productId;
	public Long price;
	public String productName;
	public Long totalSoldCount;

	private PopularProductRowWithRank(Integer rank, Long productId, Long price, String productName,
		Long totalSoldCount) {
		this.rank = rank;
		this.productId = productId;
		this.price = price;
		this.productName = productName;
		this.totalSoldCount = totalSoldCount;
	}

	public static PopularProductRowWithRank from(Integer rank, PopularProductRow row) {
		return new PopularProductRowWithRank(
			rank,
			row.getProductId(),
			row.getPrice(),
			row.getProductName(),
			row.getTotalSoldCount()
		);
	}

	public static PopularProductRowWithRank from(
		Integer rank,
		Long productId,
		Long price,
		String productName,
		Long totalSoldCount
	) {
		return new PopularProductRowWithRank(rank, productId, price, productName, totalSoldCount);
	}


}
