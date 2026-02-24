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
import org.springframework.data.redis.connection.RedisStringCommands;
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

	@Transactional(readOnly = true)
	public PopularProductsResponse getPopulars(PopularDateRange range) {
		String zsetKey = zsetKey(range);
		String oldKey = zsetKey + ":old";

		Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet()
			.reverseRangeWithScores(zsetKey, 0, POPULAR_PRODUCT_LIMIT - 1);

		if(tuples == null || tuples.isEmpty()) {
			// fallback: 배치 전이나 비어있으면 DB 집계해서 반환해줌.
			tuples = stringRedisTemplate.opsForZSet().reverseRangeWithScores(oldKey, 0, POPULAR_PRODUCT_LIMIT - 1);
			if (tuples == null || tuples.isEmpty()) {
				return queryPopularsFromDb(range);
			}
		}

		// map: {productId, soldQty}
		// 랭크 순서가 유지되는 list(productId)
		List<Long> rankedProductIds = new ArrayList<>(tuples.size());
		Map<Long, Long> soldQtyMap = new HashMap<>(tuples.size());

		for (ZSetOperations.TypedTuple<String> t : tuples) {
			if (t == null || t.getValue() == null || t.getScore() == null) continue;
			long productId = Long.parseLong(t.getValue());

			// 실제 판매량, soldQty
			long soldQty = PopularScoreCodec.decodeQty(t.getScore());

			rankedProductIds.add(productId);
			soldQtyMap.put(productId, soldQty);
		}

		if (rankedProductIds.isEmpty()) {
			return queryPopularsFromDb(range);
		}

		// Tier2: 스냅샷 캐시 MGET
		List<String> snapKeys = rankedProductIds.stream()
			.map(this::snapKey)
			.toList();

		// 캐시된 product 상세, 50개 들어있음, 없는 캐시는 null
		List<String> snapJsons = stringRedisTemplate.opsForValue().multiGet(snapKeys);

		Map<Long, ProductSnap> snapMap = new HashMap<>();
		List<Long> missedIds = new ArrayList<>();

		for(int i = 0; i < rankedProductIds.size(); i++) {
			Long pid = rankedProductIds.get(i);
			// 이렇게 전부 방어하는게 맞나? 코테에 익숙하면 방어 잘하겠네
			String json = (snapJsons == null || snapJsons.size() <= i ? null : snapJsons.get(i));

			if (json == null || json.isBlank()) {
				// 캐시에 없는 product 모으기.
				missedIds.add(pid);
				continue;
			}
			try {
				// pid랑 snapJsons에 있는 객체랑 1:1로 매핑되어야함.
				ProductSnap snap = objectMapper.readValue(json, ProductSnap.class);
				// 만약 캐시가 꼬였으면 해당 product 상세 내용을 저장 x
				if(snap != null && snap.getProductId().equals(pid)) {
					snapMap.put(pid, snap);
				} else {
					missedIds.add(pid);
				}
			} catch (Exception e) {
				// 깨진 캐시 제거하고 DB 미스로 처리
				stringRedisTemplate.delete(snapKey(pid));
				missedIds.add(pid);
			}
		}

		// 미스만 DB 조회
		if (!missedIds.isEmpty()) {
			List<Product> missProducts = productRepository.findByIdInAndIsActiveTrueAndDeletedAtIsNull(missedIds);

			// DB 결과를 snap으로 변환
			List<ProductSnap> newSnaps = missProducts.stream()
				.map(ProductSnap::from)
				.toList();

			// map에 합치기
			for (ProductSnap s : newSnaps) {
				snapMap.put(s.getProductId(), s);
			}

			// 미스만 Redis에 채우기 (pipeline으로 최적화)
			warmupProductSnaps(newSnaps, PRODUCT_SNAP_TTL);
		}

		// ZSET 순서대로 응답 구성(rank 유지)
		List<PopularProductItemResponse> items = new ArrayList<>();
		int rank = 1;

		for (Long productId : rankedProductIds) {
			ProductSnap snap = snapMap.get(productId);
			if(snap == null) continue;

			items.add(new PopularProductItemResponse(
				rank++,
				snap.getProductId(),
				snap.getName(),
				snap.getPrice(),
				soldQtyMap.getOrDefault(productId, 0L)
			));
		}

		return new PopularProductsResponse(range.days() + "d", LocalDateTime.now(), items);
	}

	// Tier2 warmup(pipeline)
	private void warmupProductSnaps(List<ProductSnap> snaps, Duration ttl) {
		if(snaps == null || snaps.isEmpty()) return;
		RedisSerializer<String> serializer = stringRedisTemplate.getStringSerializer();
		Expiration expiration = (ttl == null) ? Expiration.persistent() : Expiration.seconds(ttl.getSeconds());
		stringRedisTemplate.executePipelined((RedisCallback<Object>)connection -> {
			for (ProductSnap s : snaps) {
				try {
					String key = snapKey(s.getProductId());
					String json = objectMapper.writeValueAsString(s);

					byte[] k = serializer.serialize(key);
					byte[] v = serializer.serialize(json);
					connection.stringCommands().set(k, v, expiration, SetOption.UPSERT);
					if (ttl != null) {
						connection.expire(k, ttl.getSeconds());
					}
				} catch (Exception e) {
					// warmup 실패는 조회 결과에 영향을 주ㅕㅁㄴ 안됨
					log.warn("product snap warmup failed. productId={}", s.getProductId(), e);
				}
			}
			return null;
		});
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

	private String snapKey(long productId) {
		return "product:snap:" + productId;
	}
}

