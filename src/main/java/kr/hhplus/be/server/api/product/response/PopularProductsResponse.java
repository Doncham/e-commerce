package kr.hhplus.be.server.api.product.response;

import java.time.LocalDateTime;
import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PopularProductsResponse {
	private String range;
	private LocalDateTime generatedAt;
	private List<PopularProductItemResponse> items;

	public PopularProductsResponse(String range, LocalDateTime generatedAt, List<PopularProductItemResponse> items) {
		this.range = range;
		this.generatedAt = generatedAt;
		this.items = items;
	}
}
