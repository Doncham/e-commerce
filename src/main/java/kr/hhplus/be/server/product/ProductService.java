package kr.hhplus.be.server.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.inventory.InventoryRepository;
import kr.hhplus.be.server.inventory.InventoryStatus;
import kr.hhplus.be.server.inventory.exception.NotFoundInventoryException;
import kr.hhplus.be.server.product.response.ProductDetailResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {
	private static final int LOW_STOCK_THRESHOLD = 10;

	private final InventoryRepository inventoryRepository;

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
}
