package kr.hhplus.be.server.product.response;

import kr.hhplus.be.server.inventory.InventoryStatus;
import kr.hhplus.be.server.product.Product;
import lombok.Builder;
import lombok.Getter;

@Getter
public class ProductDetailResponse {
	private final Long product_id;
	private final String name;
	private final String description;
	private final Long price;
	private final boolean Active;
	private final String inventoryStatus;
	private final Long stock;
	@Builder
	public ProductDetailResponse(Long id, String name, String description, Long price, boolean active,
		String inventoryStatus, Long stock) {
		this.product_id = id;
		this.name = name;
		this.description = description;
		this.price = price;
		this.Active = active;
		this.inventoryStatus = inventoryStatus;
		this.stock = stock;
	}

	public static ProductDetailResponse from(Product product, InventoryStatus inventoryStatus, Long stock) {
		ProductDetailResponse dto = ProductDetailResponse.builder()
			.id(product.getId())
			.name(product.getName())
			.description(product.getDescription())
			.price(product.getPrice())
			.active(product.isActive())
			.inventoryStatus(inventoryStatus.toString())
			.stock(stock)
			.build();
		return dto;
	}
}
