package kr.hhplus.be.server.application.product;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.test.util.ReflectionTestUtils;

import kr.hhplus.be.server.api.product.response.PopularProductsResponse;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.domain.product.Product;
import kr.hhplus.be.server.infrastructure.persistence.inventory.InventoryRepository;
import kr.hhplus.be.server.infrastructure.persistence.orderproduct.OrderProductRepository;
import kr.hhplus.be.server.infrastructure.persistence.product.ProductRepository;

@ExtendWith(MockitoExtension.class)
class PopularProductZSetReadTest {

	@Mock OrderProductRepository orderProductRepository;
	@Mock ProductRepository productRepository;

	@Mock StringRedisTemplate redis;
	@Mock ZSetOperations<String, String> zsetOps;

	@InjectMocks ProductService productService;

	@BeforeEach
	void setUp() {
		given(redis.opsForZSet()).willReturn(zsetOps);
	}

	@Test
	void zsetHit_buildsResponse_fromZsetOrder_andDbProducts() {
		// given
		String key7d = "rank:zset:7d";

		// ZSET: (p2=7), (p1=2) 순서로 내려온다고 가정 (reverseRangeWithScores)
		Set<ZSetOperations.TypedTuple<String>> tuples = linkedTuples(
			tuple("2", 7.0),
			tuple("1", 2.0)
		);
		given(zsetOps.reverseRangeWithScores(eq(key7d), eq(0L), eq(19L))).willReturn(tuples);

		// DB: productId 1,2 모두 존재
		Product p1 = product(1L, "P1", 1000L);
		Product p2 = product(2L, "P2", 2000L);
		given(productRepository.findByIdInAndIsActiveTrueAndDeletedAtIsNull(List.of(2L, 1L)))
			.willReturn(List.of(p1, p2)); // 순서는 상관없음(맵으로 매핑)

		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.SEVEN);

		// then
		assertThat(res).isNotNull();
		assertThat(res.getItems()).hasSize(2);

		assertThat(res.getItems().get(0).getRank()).isEqualTo(1);
		assertThat(res.getItems().get(0).getProductId()).isEqualTo(2L);
		assertThat(res.getItems().get(0).getSoldQty()).isEqualTo(7L);

		assertThat(res.getItems().get(1).getRank()).isEqualTo(2);
		assertThat(res.getItems().get(1).getProductId()).isEqualTo(1L);
		assertThat(res.getItems().get(1).getSoldQty()).isEqualTo(2L);

		then(orderProductRepository).shouldHaveNoInteractions(); // ZSET hit이면 DB 집계 fallback 안 탐
	}



	@Test
	void finalKeyEmpty_andOldKeyEmpty_thenFallbackDbQuery() {
		// given
		String finalKey = "rank:zset:30d";

		given(zsetOps.reverseRangeWithScores(eq(finalKey), eq(0L), eq(19L))).willReturn(Set.of());

		// DB 집계 결과
		List<ProductSoldQtyDTO> agg = List.of(
			new ProductSoldQtyDTO(101L, 50L),
			new ProductSoldQtyDTO(202L, 40L)
		);
		given(orderProductRepository.findPopularProduct(any(), any(), eq(OrderStatus.PAID), any(Pageable.class)))
			.willReturn(agg);

		Product p101 = product(101L, "A", 1000L);
		Product p202 = product(202L, "B", 2000L);
		given(productRepository.findByIdInAndIsActiveTrueAndDeletedAtIsNull(List.of(101L, 202L)))
			.willReturn(List.of(p101, p202));

		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.THIRTY);

		// then
		assertThat(res.getItems()).hasSize(2);
		assertThat(res.getItems().get(0).getProductId()).isEqualTo(101L);
		assertThat(res.getItems().get(0).getSoldQty()).isEqualTo(50L);

		// DB 집계 호출 파라미터 중 pageSize=20 확인
		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		then(orderProductRepository).should()
			.findPopularProduct(any(LocalDateTime.class), any(LocalDateTime.class), eq(OrderStatus.PAID), pageableCaptor.capture());
		assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);
		assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
	}

	@Test
	void zsetHasNullOrBadTuples_skipsThem_andStillReturnsValidOnes() {
		// given
		String key = "rank:zset:7d";

		// null value / null score 섞기
		Set<ZSetOperations.TypedTuple<String>> tuples = linkedTuples(
			tuple(null, 10.0),
			tuple("1", null),
			tuple("2", 7.0)
		);
		given(zsetOps.reverseRangeWithScores(eq(key), eq(0L), eq(19L))).willReturn(tuples);

		Product p2 = product(2L, "P2", 2000L);
		given(productRepository.findByIdInAndIsActiveTrueAndDeletedAtIsNull(List.of(2L)))
			.willReturn(List.of(p2));

		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.SEVEN);

		// then
		assertThat(res.getItems()).hasSize(1);
		assertThat(res.getItems().get(0).getProductId()).isEqualTo(2L);
		assertThat(res.getItems().get(0).getSoldQty()).isEqualTo(7L);

		then(orderProductRepository).shouldHaveNoInteractions();
	}

	@Test
	void zsetHit_butSomeProductsMissingInDb_skipsMissing_andRanksAreCompacted() {
		// given
		String key = "rank:zset:7d";

		Set<ZSetOperations.TypedTuple<String>> tuples = linkedTuples(
			tuple("101", 50.0),
			tuple("202", 40.0),
			tuple("303", 30.0)
		);
		given(zsetOps.reverseRangeWithScores(eq(key), eq(0L), eq(19L))).willReturn(tuples);

		// DB에서 202가 빠짐(비활성/삭제 등)
		Product p101 = product(101L, "A", 1000L);
		Product p303 = product(303L, "C", 3000L);
		given(productRepository.findByIdInAndIsActiveTrueAndDeletedAtIsNull(List.of(101L, 202L, 303L)))
			.willReturn(List.of(p101, p303));

		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.SEVEN);

		// then
		assertThat(res.getItems()).hasSize(2);

		assertThat(res.getItems().get(0).getRank()).isEqualTo(1);
		assertThat(res.getItems().get(0).getProductId()).isEqualTo(101L);
		assertThat(res.getItems().get(0).getSoldQty()).isEqualTo(50L);

		assertThat(res.getItems().get(1).getRank()).isEqualTo(2);
		assertThat(res.getItems().get(1).getProductId()).isEqualTo(303L);
		assertThat(res.getItems().get(1).getSoldQty()).isEqualTo(30L);

		then(orderProductRepository).shouldHaveNoInteractions();
	}

	// ===== helpers =====

	private Product product(Long id, String name, Long price) {
		Product p = Product.createProduct(name, "desc", price);
		ReflectionTestUtils.setField(p, "id", id);
		return p;
	}

	private ZSetOperations.TypedTuple<String> tuple(String value, Double score) {
		// Spring이 제공하는 DefaultTypedTuple 사용 가능
		return new org.springframework.data.redis.core.DefaultTypedTuple<>(value, score);
	}

	@SafeVarargs
	private final Set<ZSetOperations.TypedTuple<String>> linkedTuples(ZSetOperations.TypedTuple<String>... tuples) {
		// "순서"를 테스트에서 보장하려고 LinkedHashSet 사용
		Set<ZSetOperations.TypedTuple<String>> set = new LinkedHashSet<>();
		for (var t : tuples) set.add(t);
		return set;
	}
}
