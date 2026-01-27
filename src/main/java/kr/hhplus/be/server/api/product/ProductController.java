package kr.hhplus.be.server.api.product;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import kr.hhplus.be.server.api.product.response.PopularProductsResponse;
import kr.hhplus.be.server.application.product.PopularDateRange;
import kr.hhplus.be.server.application.product.ProductService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/product")
@RequiredArgsConstructor
public class ProductController {
	private final ProductService productService;
	@GetMapping("/popular")
	public ResponseEntity<PopularProductsResponse> popular(
		@RequestParam PopularDateRange range
	) {
		PopularProductsResponse populars = productService.getPopulars(range);
		return ResponseEntity.ok(populars);
	}
}
