package kr.hhplus.be.server.application.product;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.infrastructure.persistence.inventory.InventoryRepository;
import kr.hhplus.be.server.domain.inventory.InventoryStatus;
import kr.hhplus.be.server.domain.inventory.exception.NotFoundInventoryException;
import kr.hhplus.be.server.api.product.ProductDetailResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProductService {
	// 재고 상태를 클라이언트에 보여주는 기준
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
