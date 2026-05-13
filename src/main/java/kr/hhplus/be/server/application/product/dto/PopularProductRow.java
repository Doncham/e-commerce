package kr.hhplus.be.server.application.product.dto;

public interface PopularProductRow {
	Long getProductId();
	String getProductName();
	Long getPrice();
	Long getTotalSoldCount();
}
