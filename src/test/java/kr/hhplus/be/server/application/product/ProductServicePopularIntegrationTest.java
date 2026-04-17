package kr.hhplus.be.server.application.product;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.api.product.response.PopularProductsResponse;
import kr.hhplus.be.server.domain.popular_product_snapshot.PopularProductSnapshot;
import kr.hhplus.be.server.infrastructure.persistence.popularproductsnapshot.PopularProductSnapshotRepository;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProductServicePopularIntegrationTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Autowired ProductService productService;
	@Autowired PopularProductSnapshotRepository popularProductSnapshotRepository;
	@Autowired ObjectMapper objectMapper;

	@MockitoBean StringRedisTemplate redis;
	@MockitoBean ValueOperations<String, String> valueOperations;
	@MockitoBean Clock clock;

	@BeforeEach
	void setUp() {
		given(redis.opsForValue()).willReturn(valueOperations);
		given(clock.getZone()).willReturn(KST);
		given(clock.withZone(KST)).willReturn(clock);
		given(clock.instant()).willReturn(Instant.parse("2026-04-15T03:00:00Z")); // KST 12:00
	}

	@Test
	void redisHit_returnsFromRedis() throws Exception {
		// given
		List<PopularProductRowWithRank> rows = List.of(
			rankedRow(1, 10L, 1000L, "AAA", 7L),
			rankedRow(2, 20L, 2000L, "BBB", 3L)
		);
		String json = objectMapper.writeValueAsString(rows);

		given(valueOperations.get("rank:7d")).willReturn(json);

		// when
		PopularProductsResponse result = productService.getPopulars(PopularDateRange.SEVEN);

		// then
		assertThat(result.getRange()).isEqualTo("7d");
		assertThat(result.getItems()).hasSize(2);
		assertThat(result.getItems()).extracting("rank").containsExactly(1, 2);
		assertThat(result.getItems()).extracting("productId").containsExactly(10L, 20L);
		assertThat(result.getItems()).extracting("totalSoldCount").containsExactly(7L, 3L);
	}

	@Test
	void redisMiss_fallsBackToSnapshot() throws Exception {
		// given
		given(valueOperations.get("rank:30d")).willReturn(null);

		List<PopularProductRowWithRank> rows = List.of(
			rankedRow(1, 101L, 1500L, "P1", 50L),
			rankedRow(2, 202L, 2500L, "P2", 40L)
		);
		String json = objectMapper.writeValueAsString(rows);
		LocalDateTime createdAt = LocalDateTime.of(2026, 4, 15, 0, 30);

		popularProductSnapshotRepository.save(
			new PopularProductSnapshot(PopularDateRange.THIRTY, json, createdAt)
		);

		// when
		PopularProductsResponse result = productService.getPopulars(PopularDateRange.THIRTY);

		// then
		assertThat(result.getRange()).isEqualTo("30d");
		assertThat(result.getGeneratedAt()).isEqualTo(createdAt);
		assertThat(result.getItems()).hasSize(2);
		assertThat(result.getItems()).extracting("productId").containsExactly(101L, 202L);
		assertThat(result.getItems()).extracting("totalSoldCount").containsExactly(50L, 40L);
	}

	@Test
	void redisAccessFail_fallsBackToSnapshot() throws Exception {
		// given
		given(valueOperations.get("rank:7d"))
			.willThrow(new DataAccessResourceFailureException("redis down"));

		List<PopularProductRowWithRank> rows = List.of(
			rankedRow(1, 999L, 9999L, "FALLBACK", 12L)
		);
		String json = objectMapper.writeValueAsString(rows);
		LocalDateTime createdAt = LocalDateTime.of(2026, 4, 15, 1, 0);

		popularProductSnapshotRepository.save(
			new PopularProductSnapshot(PopularDateRange.SEVEN, json, createdAt)
		);

		// when
		PopularProductsResponse result = productService.getPopulars(PopularDateRange.SEVEN);

		// then
		assertThat(result.getGeneratedAt()).isEqualTo(createdAt);
		assertThat(result.getItems()).hasSize(1);
		assertThat(result.getItems().get(0).getProductId()).isEqualTo(999L);
		assertThat(result.getItems().get(0).getTotalSoldCount()).isEqualTo(12L);
	}

	@Test
	void snapshotMissing_returnsEmpty() {
		// given
		given(valueOperations.get("rank:7d")).willReturn(null);

		// when
		PopularProductsResponse result = productService.getPopulars(PopularDateRange.SEVEN);

		// then
		assertThat(result.getRange()).isEqualTo("7d");
		assertThat(result.getItems()).isEmpty();
		assertThat(result.getGeneratedAt()).isNotNull();
	}

	@Test
	void brokenSnapshotJson_returnsEmpty() {
		// given
		given(valueOperations.get("rank:7d")).willReturn(null);

		LocalDateTime createdAt = LocalDateTime.of(2026, 4, 15, 2, 0);
		popularProductSnapshotRepository.save(
			new PopularProductSnapshot(PopularDateRange.SEVEN, "{broken-json", createdAt)
		);

		// when
		PopularProductsResponse result = productService.getPopulars(PopularDateRange.SEVEN);

		// then
		assertThat(result.getGeneratedAt()).isEqualTo(createdAt);
		assertThat(result.getItems()).isEmpty();
	}

	private PopularProductRowWithRank rankedRow(
		Integer rank,
		Long productId,
		Long price,
		String productName,
		Long totalSoldCount
	) {
		return PopularProductRowWithRank.from(
			rank,
			productId,
			price,
			productName,
			totalSoldCount
		);
	}
}