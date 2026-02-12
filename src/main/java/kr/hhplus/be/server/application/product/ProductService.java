package kr.hhplus.be.server.application.product;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

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
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
	// 재고 상태를 클라이언트에 보여주는 기준
	private static final int LOW_STOCK_THRESHOLD = 10;
	private static final int POPULAR_PRODUCT_LIMIT = 20;

	private final InventoryRepository inventoryRepository;
	private final OrderProductRepository orderProductRepository;
	private final ProductRepository productRepository;

	private final StringRedisTemplate stringRedisTemplate;

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
		String zsetKey = zsetKey(range);

		Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
			.reverseRangeWithScores(zsetKey, 0, POPULAR_PRODUCT_LIMIT - 1);
		if(tuples == null || tuples.isEmpty()) {
			// fallback: 배치 전이나 비어있으면 DB 집계해서 반환해줌.
			return queryPopularsFromDb(range);
		}

		// 2) productId 리스트 추출 + score(판매량) 맵 만들기
		List<Long> productIds = new ArrayList<>(tuples.size());
		Map<Long, Long> soldQtyMap = new HashMap<>(tuples.size());

		for (ZSetOperations.TypedTuple<String> t : tuples) {
			if (t.getValue() == null || t.getScore() == null) continue;
			long productId = Long.parseLong(t.getValue());
			long soldQty = t.getScore().longValue(); // score=double -> long으로 변환(정수 집계라는 전제)
			productIds.add(productId);
			soldQtyMap.put(productId, soldQty);
		}

		// 3) DB에서 상품 상세 조회
		List<Product> products = productRepository.findByIdInAndIsActiveTrueAndDeletedAtIsNull(productIds);

		Map<Long, Product> productMap = products.stream()
			.collect(Collectors.toMap(Product::getId, p -> p));

		// 4) 응답 DTO 구성 (ZSET 순서대로 rank 부여)
		List<PopularProductItemResponse> items = new ArrayList<>();
		int rank = 1;

		for (Long productId : productIds) {
			Product p = productMap.get(productId);
			if (p == null) continue;

			items.add(new PopularProductItemResponse(
				rank++,
				p.getId(),
				p.getName(),
				p.getPrice(),
				soldQtyMap.getOrDefault(productId, 0L)
			));
		}

		return new PopularProductsResponse(range.days() + "d", LocalDateTime.now(), items);

	}
	private PopularProductsResponse queryPopularsFromDb(PopularDateRange range) {
		LocalDateTime to = LocalDateTime.now();
		LocalDateTime from = to.minusDays(range.days());

		List<ProductSoldQtyDTO> popularProducts = orderProductRepository.findPopularProduct(
			from, to, OrderStatus.PAID, PageRequest.of(0, POPULAR_PRODUCT_LIMIT)
		);

		if (popularProducts.isEmpty()) {
			return new PopularProductsResponse(range.days() + "d", to, List.of());
		}

		List<Long> productIds = popularProducts.stream()
			.map(ProductSoldQtyDTO::getProductId)
			.toList();

		List<Product> products = productRepository.findByIdInAndIsActiveTrueAndDeletedAtIsNull(productIds);

		Map<Long, Product> productMap = products.stream()
			.collect(Collectors.toMap(Product::getId, Function.identity()));

		ArrayList<PopularProductItemResponse> result = new ArrayList<>(popularProducts.size());
		int rank = 1;

		for (ProductSoldQtyDTO row : popularProducts) {
			Product p = productMap.get(row.getProductId());
			if (p == null) continue;

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

	private String zsetKey(PopularDateRange range) {
		return switch (range) {
			case SEVEN -> "rank:zset:7d";
			case THIRTY -> "rank:zset:30d";
		};
	}
}

