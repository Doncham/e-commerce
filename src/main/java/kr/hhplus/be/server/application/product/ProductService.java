package kr.hhplus.be.server.application.product;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

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
import kr.hhplus.be.server.domain.inventory.InventoryStatus;
import kr.hhplus.be.server.domain.inventory.exception.NotFoundInventoryException;
import kr.hhplus.be.server.domain.popular_product_snapshot.PopularProductSnapshot;
import kr.hhplus.be.server.infrastructure.persistence.inventory.InventoryRepository;
import kr.hhplus.be.server.infrastructure.persistence.popularproductsnapshot.PopularProductSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductService {
	// 재고 상태를 클라이언트에 보여주는 기준
	private static final int LOW_STOCK_THRESHOLD = 10;

	private final InventoryRepository inventoryRepository;
	private final PopularProductSnapshotRepository popularProductSnapshotRepo;

	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;

	private final Clock clock;
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");


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
		String key = cacheKey(range);
		List<PopularProductRowWithRank> popularList;
		try{
			String json = redis.opsForValue().get(key);
			// 배치돌릴 때 빈 캐시라도 넣어둬야함. null이면 뭔가 문제 생긴거야.
			if(json == null || json.isBlank()) {
				log.warn("popular cache miss. key={}, range={}", key, range);
				return getPopularsSnapshotFromDB(range);
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
			return getPopularsSnapshotFromDB(range);
		} catch (DataAccessException e) {
			log.warn("popular cache access failed. key={}, range={}", key, range, e);
			return getPopularsSnapshotFromDB(range);
		}
	}


	private PopularProductsResponse getPopularsSnapshotFromDB(PopularDateRange range) {

		PopularProductSnapshot snapshot = popularProductSnapshotRepo.findByRangeType(range)
			.orElse(null);

		if(snapshot == null) {
			log.error("popular snapshot not found. range={}", range);
			return new PopularProductsResponse(
				range.days() + "d",
				LocalDateTime.now(clock.withZone(KST)),
				List.of()
			);
		}

		String json = snapshot.getJson();
		LocalDateTime createdAt = snapshot.getCreatedAt();

		if(json == null || json.isEmpty()) {
			log.error("DB popular_product_snapshot이 없습니다. createdAt ={}", createdAt);
			return new PopularProductsResponse(range.days() + "d", createdAt, List.of());
		}
		try {
			List<PopularProductRowWithRank> popularList = objectMapper.readValue(json,
				new TypeReference<List<PopularProductRowWithRank>>() {
				});
			List<PopularProductItemResponse> items = popularList.stream()
				.map(PopularProductItemResponse::from)
				.toList();

			return new PopularProductsResponse(range.days() + "d", createdAt, items);

		} catch (JsonProcessingException e) {
			log.error("DB popular_product_snapshot이 깨졌습니다. createdAt ={}", createdAt);
			return new PopularProductsResponse(range.days() + "d", createdAt, List.of());
		}
	}

	private String cacheKey(PopularDateRange range){
		return switch (range) {
			case SEVEN -> "rank:7d";
			case THIRTY -> "rank:30d";
			case TEST -> "rank:testd";
		};
	}
}

