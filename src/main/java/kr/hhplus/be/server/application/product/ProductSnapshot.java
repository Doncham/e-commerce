package kr.hhplus.be.server.application.product;

import kr.hhplus.be.server.domain.product.Product;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ProductSnapshot {
	private Long productId;
	private String name;
	private Long price;

	public ProductSnapshot(Long productId, String name, Long price) {
		this.productId = productId;
		this.name = name;
		this.price = price;
	}

	public static ProductSnapshot from(Product p) {
		return new ProductSnapshot(p.getId(), p.getName(), p.getPrice());
	}
}
