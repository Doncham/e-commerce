package kr.hhplus.be.server.application.product;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.api.product.response.PopularProductsResponse;
import kr.hhplus.be.server.domain.popular_product_snapshot.PopularProductSnapshot;
import kr.hhplus.be.server.infrastructure.persistence.inventory.InventoryRepository;
import kr.hhplus.be.server.infrastructure.persistence.popularproductsnapshot.PopularProductSnapshotRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProductServicePopularReadTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Mock InventoryRepository inventoryRepository;
	@Mock PopularProductSnapshotRepository popularProductSnapshotRepository;
	@Mock StringRedisTemplate redis;
	@Mock ValueOperations<String, String> valueOperations;
	@Mock ObjectMapper objectMapper;

	ProductService productService;
	Clock clock;

	@BeforeEach
	void setUp() {
		clock = Clock.fixed(
			Instant.parse("2026-04-15T03:00:00Z"), // KST 12:00
			KST
		);
		given(redis.opsForValue()).willReturn(valueOperations);

		productService = new ProductService(
			inventoryRepository,
			popularProductSnapshotRepository,
			redis,
			objectMapper,
			clock
		);
	}

	@Test
	void redisHit_returnsFromRedis_only() throws Exception {
		// given
		String key = "rank:7d";
		String json = "REDIS_JSON";

		List<PopularProductRowWithRank> rows = List.of(
			PopularProductRowWithRank.from(
				1,
				popularRow(2L, "P2", 2000L,7L)
			),
			PopularProductRowWithRank.from(
				2,
				popularRow(1L, "P1", 1000L, 2L)
			)
		);

		given(valueOperations.get(key)).willReturn(json);
		given(objectMapper.readValue(eq(json), any(TypeReference.class))).willReturn(rows);

		// when
		PopularProductsResponse result = productService.getPopulars(PopularDateRange.SEVEN);

		// then
		assertThat(result.getItems()).hasSize(2);
		assertThat(result.getItems().get(0).getRank()).isEqualTo(1);
		assertThat(result.getItems().get(0).getProductId()).isEqualTo(2L);
		assertThat(result.getItems().get(0).getTotalSoldCount()).isEqualTo(7L);

		assertThat(result.getItems().get(1).getRank()).isEqualTo(2);
		assertThat(result.getItems().get(1).getProductId()).isEqualTo(1L);
		assertThat(result.getItems().get(1).getTotalSoldCount()).isEqualTo(2L);

		then(popularProductSnapshotRepository).shouldHaveNoInteractions();
	}

	@Test
	void redisMiss_fallsBackToDbSnapshot() throws Exception {
		// given
		String key = "rank:7d";
		String snapshotJson = "SNAPSHOT_JSON";
		LocalDateTime createdAt = LocalDateTime.of(2026, 4, 15, 0, 30);

		PopularProductSnapshot snapshot = mock(PopularProductSnapshot.class);
		given(snapshot.getJson()).willReturn(snapshotJson);
		given(snapshot.getCreatedAt()).willReturn(createdAt);

		List<PopularProductRowWithRank> rows = List.of(
			PopularProductRowWithRank.from(
				1,
				popularRow(10L, "A", 1000L, 50L)
			)
		);

		given(valueOperations.get(key)).willReturn(null);
		given(popularProductSnapshotRepository.findByRangeType(PopularDateRange.SEVEN))
			.willReturn(Optional.of(snapshot));
		given(objectMapper.readValue(eq(snapshotJson), any(TypeReference.class))).willReturn(rows);

		// when
		PopularProductsResponse result = productService.getPopulars(PopularDateRange.SEVEN);

		// then
		assertThat(result.getItems()).hasSize(1);
		assertThat(result.getItems().get(0).getProductId()).isEqualTo(10L);
		assertThat(result.getItems().get(0).getTotalSoldCount()).isEqualTo(50L);
		assertThat(result.getGeneratedAt()).isEqualTo(createdAt);
	}

	@Test
	void redisAccessFail_fallsBackToDbSnapshot() throws Exception {
		// given
		String key = "rank:30d";
		String snapshotJson = "SNAPSHOT_JSON";
		LocalDateTime createdAt = LocalDateTime.of(2026, 4, 15, 0, 45);

		PopularProductSnapshot snapshot = mock(PopularProductSnapshot.class);
		given(snapshot.getJson()).willReturn(snapshotJson);
		given(snapshot.getCreatedAt()).willReturn(createdAt);

		List<PopularProductRowWithRank> rows = List.of(
			PopularProductRowWithRank.from(
				1,
				popularRow(202L, "B", 2000L, 40L)
			)
		);

		given(valueOperations.get(key))
			.willThrow(new DataAccessResourceFailureException("redis down"));
		given(popularProductSnapshotRepository.findByRangeType(PopularDateRange.THIRTY))
			.willReturn(Optional.of(snapshot));
		given(objectMapper.readValue(eq(snapshotJson), any(TypeReference.class))).willReturn(rows);

		// when
		PopularProductsResponse result = productService.getPopulars(PopularDateRange.THIRTY);

		// then
		assertThat(result.getItems()).hasSize(1);
		assertThat(result.getItems().get(0).getProductId()).isEqualTo(202L);
		assertThat(result.getItems().get(0).getTotalSoldCount()).isEqualTo(40L);
		assertThat(result.getGeneratedAt()).isEqualTo(createdAt);
	}

	@Test
	void redisJsonBroken_fallsBackToDbSnapshot() throws Exception {
		// given
		String key = "rank:7d";
		String redisJson = "BROKEN_REDIS_JSON";
		String snapshotJson = "SNAPSHOT_JSON";
		LocalDateTime createdAt = LocalDateTime.of(2026, 4, 15, 1, 0);

		PopularProductSnapshot snapshot = mock(PopularProductSnapshot.class);
		given(snapshot.getJson()).willReturn(snapshotJson);
		given(snapshot.getCreatedAt()).willReturn(createdAt);

		List<PopularProductRowWithRank> snapshotRows = List.of(
			PopularProductRowWithRank.from(
				1,
				popularRow(999L, "Fallback", 3000L, 12L)
			)
		);

		given(valueOperations.get(key)).willReturn(redisJson);
		given(objectMapper.readValue(eq(redisJson), any(TypeReference.class)))
			.willThrow(JsonProcessingException.class);
		given(popularProductSnapshotRepository.findByRangeType(PopularDateRange.SEVEN))
			.willReturn(Optional.of(snapshot));
		given(objectMapper.readValue(eq(snapshotJson), any(TypeReference.class))).willReturn(snapshotRows);

		// when
		PopularProductsResponse result = productService.getPopulars(PopularDateRange.SEVEN);

		// then
		assertThat(result.getItems()).hasSize(1);
		assertThat(result.getItems().get(0).getProductId()).isEqualTo(999L);
		assertThat(result.getGeneratedAt()).isEqualTo(createdAt);
	}

	@Test
	void snapshotNotFound_returnsEmpty() {
		// given
		String key = "rank:7d";

		given(valueOperations.get(key)).willReturn(null);
		given(popularProductSnapshotRepository.findByRangeType(PopularDateRange.SEVEN))
			.willReturn(Optional.empty());

		// when
		PopularProductsResponse result = productService.getPopulars(PopularDateRange.SEVEN);

		// then
		assertThat(result.getItems()).isEmpty();
	}

	@Test
	void snapshotJsonBroken_returnsEmpty() throws Exception {
		// given
		String key = "rank:30d";
		String snapshotJson = "BROKEN_SNAPSHOT_JSON";
		LocalDateTime createdAt = LocalDateTime.of(2026, 4, 15, 2, 0);

		PopularProductSnapshot snapshot = mock(PopularProductSnapshot.class);
		given(snapshot.getJson()).willReturn(snapshotJson);
		given(snapshot.getCreatedAt()).willReturn(createdAt);

		given(valueOperations.get(key)).willReturn(null);
		given(popularProductSnapshotRepository.findByRangeType(PopularDateRange.THIRTY))
			.willReturn(Optional.of(snapshot));
		given(objectMapper.readValue(eq(snapshotJson), any(TypeReference.class)))
			.willThrow(mock(JsonProcessingException.class));

		// when
		PopularProductsResponse result = productService.getPopulars(PopularDateRange.THIRTY);

		// then
		assertThat(result.getItems()).isEmpty();
		assertThat(result.getGeneratedAt()).isEqualTo(createdAt);
	}
	private PopularProductRow popularRow(
		Long productId,
		String productName,
		Long price,
		Long totalSoldCount
	) {
		PopularProductRow row = mock(PopularProductRow.class);
		given(row.getProductId()).willReturn(productId);
		given(row.getProductName()).willReturn(productName);
		given(row.getPrice()).willReturn(price);
		given(row.getTotalSoldCount()).willReturn(totalSoldCount);
		return row;
	}
}
