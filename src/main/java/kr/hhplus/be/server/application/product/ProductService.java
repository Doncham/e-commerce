package kr.hhplus.be.server.application.product;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.api.product.response.PopularProductItemResponse;
import kr.hhplus.be.server.api.product.response.PopularProductsResponse;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.domain.product.Product;
import kr.hhplus.be.server.infrastructure.persistence.inventory.InventoryRepository;
import kr.hhplus.be.server.domain.inventory.InventoryStatus;
import kr.hhplus.be.server.domain.inventory.exception.NotFoundInventoryException;
import kr.hhplus.be.server.api.product.ProductDetailResponse;
import kr.hhplus.be.server.infrastructure.persistence.orderproduct.OrderProductRepository;
import kr.hhplus.be.server.infrastructure.persistence.product.ProductRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {
	// 재고 상태를 클라이언트에 보여주는 기준
	private static final int LOW_STOCK_THRESHOLD = 10;
	private static final int POPULAR_PRODUCT_LIMIT = 20;

	private final InventoryRepository inventoryRepository;
	private final OrderProductRepository orderProductRepository;
	private final ProductRepository productRepository;

	@Transactional(readOnly = true)
	public ProductDetailResponse getProductDetail(long productId) {
		// JWT 추가 후 유저 검증 추가

		return inventoryRepository.findByProductId(productId).map(inventory -> {
			Long availableStock = inventory.availableStock();
			InventoryStatus inventoryStatus = InventoryStatus.from(availableStock, LOW_STOCK_THRESHOLD);
			if (availableStock > LOW_STOCK_THRESHOLD) availableStock = null; // 숨김 처리
			return ProductDetailResponse.from(
				inventory.getProduct(),
				inventoryStatus,
				availableStock
			);
		}).orElseThrow(() -> new NotFoundInventoryException(String.valueOf(productId)));
	}

	@Transactional(readOnly = true)
	public PopularProductsResponse getPopulars(PopularDateRange range) {
		LocalDateTime to = LocalDateTime.now();
		LocalDateTime from = to.minusDays(range.days());

		List<ProductSoldQtyDTO> popularProducts = orderProductRepository.findPopularProduct(
			from,
			to,
			OrderStatus.PAID,
			PageRequest.of(0, POPULAR_PRODUCT_LIMIT)
		);

		if(popularProducts.isEmpty())
			return new PopularProductsResponse(range.days() +"d", to, List.of());

		// productIds 목록 조회
		List<Long> productIds = popularProducts.stream()
			.map(ProductSoldQtyDTO::getProductId)
			.collect(Collectors.toList());

		// 상품 정보 한번에 조회
		List<Product> products = productRepository.findByIdInAndIsActiveTrueAndDeletedAtIsNull(
			productIds);

		// 매칭용 Map
		Map<Long, Product> productMap = products.stream()
			.collect(Collectors.toMap(Product::getId, p -> p));

		ArrayList<PopularProductItemResponse> result = new ArrayList<>(popularProducts.size());
		int rank = 1;

		for(ProductSoldQtyDTO row : popularProducts) {
			Product p = productMap.get(row.getProductId());
			// 갑자기 상품이 비활성화되면 그럴 수 있음. 이거에 대한 전략은 일단 해당 상품 배제.
			if(p==null) continue;
				//return new PopularProductsResponse(range.days() + "d", LocalDateTime.now(), List.of());

			result.add(new PopularProductItemResponse(
				rank++,
				p.getId(),
				p.getName(),
				p.getPrice(),
				row.getSoldQty()
			));
		}

		return new PopularProductsResponse(range.days() + "d", to, result);
	}
}
