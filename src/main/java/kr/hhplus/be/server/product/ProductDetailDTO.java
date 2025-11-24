package kr.hhplus.be.server.product;

import kr.hhplus.be.server.inventory.InventoryStatus;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;

@Getter
public class ProductDetailDTO {
	private final Long id;
	private final String name;
	private final String description;
	private final Long price;
	private final boolean isActive;
	private final String inventoryStatus;
	private final Long stock;
	@Builder
	public ProductDetailDTO(Long id, String name, String description, Long price, boolean isActive,
		String inventoryStatus, Long stock) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.price = price;
		this.isActive = isActive;
		this.inventoryStatus = inventoryStatus;
		this.stock = stock;
	}

	public static ProductDetailDTO from(Product product, InventoryStatus inventoryStatus, Long stock) {
		ProductDetailDTO dto = ProductDetailDTO.builder()
			.id(product.getId())
			.name(product.getName())
			.description(product.getDescription())
			.price(product.getPrice())
			.isActive(product.isActive())
			.inventoryStatus(inventoryStatus.toString())
			.stock(stock)
			.build();
		return dto;
	}
}
