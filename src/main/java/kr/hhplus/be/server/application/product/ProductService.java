package kr.hhplus.be.server.application.product;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
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
	// 이런 클래스가 있네?
	private static final Duration POPULAR_CACHE_TTL = Duration.ofSeconds(120);
	// waitTime: 락 기다릴 시간, leaseTime: 락 자동 해제 시간(너무 길지 않게)
	private static final long LOCK_WAIT_MS = 200;   // 200ms만 기다리고 실패하면 fallback
	private static final long LOCK_LEASE_MS = 2_000; // 2초 후 자동 해제(쿼리 시간 여유 고려)

	// 락 실패 시 재시도(캐시가 곧 채워질 거라는 기대)
	private static final int AFTER_LOCK_FAIL_RETRY = 3;
	private static final long AFTER_LOCK_FAIL_SLEEP_MS = 50;


	private final InventoryRepository inventoryRepository;
	private final OrderProductRepository orderProductRepository;
	private final ProductRepository productRepository;

	private final StringRedisTemplate stringRedisTemplate;
	private final ObjectMapper objectMapper;
	private final RedissonClient redissonClient;

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
		String cacheKey = popularCacheKey(range);
		String lockKey = popularLockKey(range);

		// 1) 캐시 먼저 확인
		PopularProductsResponse cached = getCache(cacheKey);
		if (cached != null) return cached;

		// 여기서부터는 cache가 없어서 DB에 직접 접근하는 부분
		// 2) 분산 락으로 스탬피드 방지
		RLock lock = redissonClient.getLock(lockKey);
		boolean locked = false;

		try {
			 locked = lock.tryLock(LOCK_WAIT_MS, LOCK_LEASE_MS, TimeUnit.MILLISECONDS);
			 if(locked) {
				 // 3) 락을 얻은 후에 다시 캐시 확인(더블 체크)
				 PopularProductsResponse cache2 = getCache(cacheKey);
				 if(cache2 !=null) return cache2;

				 // 4) DB 조회 후 캐시 set
				 PopularProductsResponse fresh = queryPopularsFromDb(range);
				 setCache(cacheKey, fresh, POPULAR_CACHE_TTL);
				 return fresh;
			 }
			// 5) 락을 못 얻은 경우
			// 다른 인스턴스가 갱신중일 확률이 높으니 짧게 기다렸다가 캐시 재조회
			for(int i = 0; i < AFTER_LOCK_FAIL_RETRY; i++) {
				sleepSilently(AFTER_LOCK_FAIL_SLEEP_MS);
				PopularProductsResponse retry = getCache(cacheKey);
				if(retry != null)
					return retry;
			}

			// 6) 이래도 캐시가 없으면 fallback: DB 조회
			return queryPopularsFromDb(range);

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			// 인터럽트 발생 시에도 fallback로 DB 조회 혹은 빈값 반환 중 선택
			return queryPopularsFromDb(range);
		} finally {
			if (locked) {
				// 락 소유자만 unlockㅖ
				lock.unlock();
			}
		}

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

	private String popularCacheKey(PopularDateRange range) {
		return "popular:" + range.name(); // popular:SEVEN
	}

	private String popularLockKey(PopularDateRange range) {
		return "lock:popular:" + range.name(); // lock:popular:SEVEN
	}
	private PopularProductsResponse getCache(String key) {
		String json = stringRedisTemplate.opsForValue().get(key);
		if (json == null || json.isBlank()) return null;

		try {
			return objectMapper.readValue(json, PopularProductsResponse.class);
		} catch (Exception e) {
			// 역직렬화 실패하면 캐시가 깨진 것으로 간주하고 제거 + miss 처리
			stringRedisTemplate.delete(key);
			return null;
		}
	}

	private void setCache(String key, PopularProductsResponse value, Duration ttl) {
		try {
			String json = objectMapper.writeValueAsString(value);
			stringRedisTemplate.opsForValue().set(key, json, ttl);
		} catch (JsonProcessingException e) {
			// 직렬화 실패 시 캐시 저장 생략(조회는 DB 결과로 진행)
			log.error("캐시 직렬화 실패");
		}
	}

	private void sleepSilently(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	public void evictPopularCacheAllRanges() {
		// 멀티 인스턴스에서도 Redis DEL이면 즉시 무효화됨
		stringRedisTemplate.delete(popularCacheKey(PopularDateRange.SEVEN));
		stringRedisTemplate.delete(popularCacheKey(PopularDateRange.THIRTY));
	}
}

