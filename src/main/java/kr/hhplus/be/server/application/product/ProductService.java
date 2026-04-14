package kr.hhplus.be.server.application.product;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.api.product.ProductDetailResponse;
import kr.hhplus.be.server.api.product.response.PopularProductItemResponse;
import kr.hhplus.be.server.api.product.response.PopularProductsResponse;
import kr.hhplus.be.server.application.cache.PopularLocalCache;
import kr.hhplus.be.server.domain.inventory.InventoryStatus;
import kr.hhplus.be.server.domain.inventory.exception.NotFoundInventoryException;
import kr.hhplus.be.server.infrastructure.persistence.inventory.InventoryRepository;
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
	private final PopularProductRefreshService popularProductRefreshService;

	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;

	private final Clock clock;
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private final PopularLocalCache localCache;


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
		// range로 키 만들기
		String key = cacheKey(range);
		List<PopularProductRowWithRank> popularList;
		try{
			String json = redis.opsForValue().get(key);
			// 배치돌릴 때 빈 캐시라도 넣어둬야함. null이면 뭔가 문제 생긴거야.
			if(json == null || json.isBlank()) {
				// DB fallback? or 빈 리스트 던지기, 레디스 장애가 생기면 DB 조회해서 반환하는게 맞는듯
				// 가변게 오늘 인기상품에 대한 집계 코드를 만들어서 반환해줄까? 7/30일은 너무 많아질 수 있잖아
				log.warn("popular cache miss. key={}, range={}", key, range);
				return getPopularsWithLocalCache(range);
			}
			popularList =
				objectMapper.readValue(json, new TypeReference<List<PopularProductRowWithRank>>() {});

			List<PopularProductItemResponse> items = popularList.stream()
				.map(PopularProductItemResponse::from)
				.toList();

			return new PopularProductsResponse(range.days() + "d", LocalDateTime.now(clock.withZone(KST)), items);
		} catch (JsonProcessingException e) {
			// 캐시 깨졌을 때 바로 예외 터뜨리는건 좀 아니다. DB 조회해서 반환해야함.
			log.warn("popular cache parse failed. key={}, range={}", key, range, e);
			return getPopularsWithLocalCache(range);
		} catch (DataAccessException e) {
			log.warn("popular cache access failed. key={}, range={}", key, range, e);
			return getPopularsWithLocalCache(range);
		}
	}


	private PopularProductsResponse getPopularsWithLocalCache(PopularDateRange range) {
		String cacheKey = localCacheKey(range);

		return localCache.getOrLoad(cacheKey, k -> {
			List<PopularProductRowWithRank> popularProductRowWithRanks = popularProductRefreshService.getPopularProductRowWithRanks(
				range.days());

			List<PopularProductItemResponse> popularResponses = popularProductRowWithRanks.stream()
				.map(PopularProductItemResponse::from)
				.collect(Collectors.toList());

			// 인기 상품이 없어도 빈 리스트가 캐시에 저장됨.
			return new PopularProductsResponse(
				range.days() + "d",
				LocalDateTime.now(clock.withZone(KST)),
				popularResponses);
		});
	}

	private String cacheKey(PopularDateRange range){
		return switch (range) {
			case SEVEN -> "rank:7d";
			case THIRTY -> "rank:30d";
			case TEST -> "rank:testd";
		};
	}

	private String localCacheKey(PopularDateRange range) {
		return "popular:" + range.name();
	}
}

