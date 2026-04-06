package kr.hhplus.be.server.application.product;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.api.product.ProductDetailResponse;
import kr.hhplus.be.server.api.product.response.PopularProductItemResponse;
import kr.hhplus.be.server.api.product.response.PopularProductsResponse;
import kr.hhplus.be.server.domain.inventory.InventoryStatus;
import kr.hhplus.be.server.domain.inventory.exception.NotFoundInventoryException;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.domain.product.Product;
import kr.hhplus.be.server.infrastructure.persistence.inventory.InventoryRepository;
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

	private static final Duration PRODUCT_SNAP_TTL = Duration.ofDays(7);

	private final InventoryRepository inventoryRepository;
	private final OrderProductRepository orderProductRepository;
	private final ProductRepository productRepository;

	private final StringRedisTemplate stringRedisTemplate;
	private final ObjectMapper objectMapper;

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

	// 나중에 Facade + cacheWarmer로 분리해서 진짜 조회만 하는 방향으로 리팩토링 ㄱㄱ
	@Transactional(readOnly = true)
	public PopularProductsResponse getPopulars(PopularDateRange range) {
		List<RankedProduct> rankedProducts = loadRankedProducts(range);

		if (rankedProducts.isEmpty()) {
			return queryPopularsFromDb(range);
		}

		Map<Long, ProductSnapshot> snapMap = loadProductSnapshotMap(rankedProducts);
		List<PopularProductItemResponse> items = toPopularItems(rankedProducts, snapMap);

		return new PopularProductsResponse(range.days() + "d", LocalDateTime.now(), items);
	}

	private List<PopularProductItemResponse> toPopularItems(List<RankedProduct> rankedProducts,
		Map<Long, ProductSnapshot> snapMap) {
		List<PopularProductItemResponse> items = new ArrayList<>();
		int rank = 1;

		for (RankedProduct ranked : rankedProducts) {
			ProductSnapshot snap = snapMap.get(ranked.productId());
			if (snap == null) {
				continue;
			}

			items.add(new PopularProductItemResponse(
				rank++,
				snap.getProductId(),
				snap.getName(),
				snap.getPrice(),
				ranked.soldQty()
			));
		}

		return items;
	}

	private Map<Long, ProductSnapshot> loadProductSnapshotMap(List<RankedProduct> rankedProducts) {
		Map<Long, String> productSnapshotJsonMap = loadProductSnapshotJsonMap(rankedProducts);

		Map<Long, ProductSnapshot> productSnapshotMap = new HashMap<>();
		List<Long> missedIds = new ArrayList<>();

		for (RankedProduct ranked : rankedProducts) {
			Long productId = ranked.productId();
			String json = productSnapshotJsonMap.get(productId);

			if (json == null || json.isBlank()) {
				missedIds.add(productId);
				continue;
			}

			try {
				ProductSnapshot productSnapshot = objectMapper.readValue(json, ProductSnapshot.class);
				if (productSnapshot != null && productSnapshot.getProductId().equals(productId)) {
					productSnapshotMap.put(productId, productSnapshot);
				} else {
					missedIds.add(productId);
				}
			} catch (Exception e) {
				stringRedisTemplate.delete(productSnapshotKey(productId));
				missedIds.add(productId);
			}
		}

		if (!missedIds.isEmpty()) {
			List<Product> missProducts = productRepository.findByIdInAndIsActiveTrueAndDeletedAtIsNull(missedIds);
			List<ProductSnapshot> newProductSnapshots = missProducts.stream()
				.map(ProductSnapshot::from)
				.toList();

			for (ProductSnapshot productSnapshot : newProductSnapshots) {
				productSnapshotMap.put(productSnapshot.getProductId(), productSnapshot);
			}

			warmUpProductSnaps(newProductSnapshots, PRODUCT_SNAP_TTL);
		}

		return productSnapshotMap;
	}

	private Map<Long, String> loadProductSnapshotJsonMap(List<RankedProduct> rankedProducts) {
		List<Long> productIds = rankedProducts.stream()
			.map(RankedProduct::productId)
			.toList();

		List<String> keys = productIds.stream()
			.map(this::productSnapshotKey)
			.toList();

		List<String> values = stringRedisTemplate.opsForValue().multiGet(keys);

		Map<Long, String> result = new HashMap<>();
		for (int i = 0; i < productIds.size(); i++) {
			String json = (values == null || values.size() <= i) ? null : values.get(i);
			result.put(productIds.get(i), json);
		}
		return result;
	}

	private List<RankedProduct> loadRankedProducts(PopularDateRange range) {
		String zsetKey = zsetKey(range);
		String oldKey = zsetKey + ":old";

		Set<ZSetOperations.TypedTuple<String>> tuples =
			stringRedisTemplate.opsForZSet().reverseRangeWithScores(zsetKey, 0, POPULAR_PRODUCT_LIMIT - 1);

		if (tuples == null || tuples.isEmpty()) {
			tuples = stringRedisTemplate.opsForZSet().reverseRangeWithScores(oldKey, 0, POPULAR_PRODUCT_LIMIT - 1);
			if (tuples == null || tuples.isEmpty()) {
				return List.of();
			}
		}

		return toRankedProducts(tuples);
	}

	private List<RankedProduct> toRankedProducts(Set<ZSetOperations.TypedTuple<String>> tuples) {
		List<RankedProduct> result = new ArrayList<>();
		for (ZSetOperations.TypedTuple<String> t : tuples) {
			if (t == null || t.getValue() == null || t.getScore() == null) {
				continue;
			}

			long productId = Long.parseLong(t.getValue());
			long soldQty = PopularScoreCodec.decodeQty(t.getScore());
			result.add(new RankedProduct(productId, soldQty));
		}
		return result;
	}


	// Tier2 warmup(pipeline)
	private void warmUpProductSnaps(List<ProductSnapshot> productSnapshots, Duration ttl) {
		if(productSnapshots == null || productSnapshots.isEmpty()) return;
		RedisSerializer<String> serializer = stringRedisTemplate.getStringSerializer();
		Expiration expiration = (ttl == null) ? Expiration.persistent() : Expiration.seconds(ttl.getSeconds());
		stringRedisTemplate.executePipelined((RedisCallback<Object>)connection -> {
			for (ProductSnapshot s : productSnapshots) {
				try {
					String key = productSnapshotKey(s.getProductId());
					String json = objectMapper.writeValueAsString(s);

					byte[] k = serializer.serialize(key);
					byte[] v = serializer.serialize(json);
					connection.stringCommands().set(k, v, expiration, SetOption.UPSERT);
					if (ttl != null) {
						connection.expire(k, ttl.getSeconds());
					}
				} catch (Exception e) {
					// warmup 실패는 조회 결과에 영향을 주면 안됨
					log.warn("product snap warmup failed. productId={}", s.getProductId(), e);
				}
			}
			return null;
		});
	}

	// 여기서도 warmup 해야할듯? 조회 메서드에 캐시 갱신까지 책임이 들어가면 관리 포인트가 많아짐.
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

	private String productSnapshotKey(long productId) {
		return "product:snap:" + productId;
	}
}

