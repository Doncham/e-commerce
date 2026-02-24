package kr.hhplus.be.server.application.product;

import kr.hhplus.be.server.domain.product.Product;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ProductSnap {
	private Long productId;
	private String name;
	private Long price;

	public ProductSnap(Long productId, String name, Long price) {
		this.productId = productId;
		this.name = name;
		this.price = price;
	}

	public static ProductSnap from(Product p) {
		return new ProductSnap(p.getId(), p.getName(), p.getPrice());
	}
}
