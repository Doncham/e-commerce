package kr.hhplus.be.server.application.product;

public interface PopularProductRow {
	Long getProductId();
	String getProductName();
	Long getPrice();
	Long getSoldQty();
	Long getTotalCount();
}
