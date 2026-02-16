package kr.hhplus.be.server.application.product;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.*;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.api.product.response.PopularProductsResponse;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.domain.product.Product;
import kr.hhplus.be.server.infrastructure.persistence.inventory.InventoryRepository;
import kr.hhplus.be.server.infrastructure.persistence.orderproduct.OrderProductRepository;
import kr.hhplus.be.server.infrastructure.persistence.product.ProductRepository;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PopularProductZSetReadTest {

	@Mock InventoryRepository inventoryRepository;
	@Mock OrderProductRepository orderProductRepository;
	@Mock ProductRepository productRepository;

	@Mock StringRedisTemplate redis;
	@Mock ZSetOperations<String, String> zsetOps;
	@Mock ValueOperations<String, String> valueOps;

	@Mock ObjectMapper objectMapper;

	ProductService productService;

	@BeforeEach
	void setUp() {
		given(redis.opsForZSet()).willReturn(zsetOps);
		given(redis.opsForValue()).willReturn(valueOps);

		// @InjectMocks 대신 직접 생성(주입 누락으로 인한 NPE 방지)
		productService = new ProductService(
			inventoryRepository,
			orderProductRepository,
			productRepository,
			redis,
			objectMapper
		);
	}

	@Test
	void zsetHit_andSnapAllHit_returnsFromSnapCache_only() throws Exception {
		// given
		String key = "rank:zset:7d";

		Set<ZSetOperations.TypedTuple<String>> tuples = linkedTuples(
			tuple("2", PopularScoreCodec.encode(7, 2)),
			tuple("1", PopularScoreCodec.encode(2, 1))
		);
		given(zsetOps.reverseRangeWithScores(eq(key), eq(0L), eq(19L))).willReturn(tuples);

		// multiGet 결과에 null이 없으니 List.of 사용해도 OK
		given(valueOps.multiGet(eq(List.of("product:snap:2", "product:snap:1"))))
			.willReturn(List.of("JSON2", "JSON1"));

		ProductSnap s2 = snapMock(2L, "P2", 2000L);
		ProductSnap s1 = snapMock(1L, "P1", 1000L);

		given(objectMapper.readValue("JSON2", ProductSnap.class)).willReturn(s2);
		given(objectMapper.readValue("JSON1", ProductSnap.class)).willReturn(s1);

		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.SEVEN);

		// then
		assertThat(res.getItems()).hasSize(2);
		assertThat(res.getItems().get(0).getRank()).isEqualTo(1);
		assertThat(res.getItems().get(0).getProductId()).isEqualTo(2L);
		assertThat(res.getItems().get(0).getSoldQty()).isEqualTo(7L);

		assertThat(res.getItems().get(1).getRank()).isEqualTo(2);
		assertThat(res.getItems().get(1).getProductId()).isEqualTo(1L);
		assertThat(res.getItems().get(1).getSoldQty()).isEqualTo(2L);

		then(orderProductRepository).shouldHaveNoInteractions();
		then(productRepository).shouldHaveNoInteractions();
		//then(redis).should(never()).executePipelined(any());
	}

	@Test
	void zsetHit_andSnapPartialMiss_queriesDbForMiss_andWarmsUpMiss() throws Exception {
		// given
		String key = "rank:zset:7d";

		Set<ZSetOperations.TypedTuple<String>> tuples = linkedTuples(
			tuple("2", PopularScoreCodec.encode(7, 2)),
			tuple("1", PopularScoreCodec.encode(2, 1))
		);
		given(zsetOps.reverseRangeWithScores(eq(key), eq(0L), eq(19L))).willReturn(tuples);

		// ✅ null을 포함해야 하므로 List.of("JSON2", null) 쓰면 NPE 터짐
		given(valueOps.multiGet(eq(List.of("product:snap:2", "product:snap:1"))))
			.willReturn(Arrays.asList("JSON2", null));

		ProductSnap s2 = snapMock(2L, "P2", 2000L);
		given(objectMapper.readValue("JSON2", ProductSnap.class)).willReturn(s2);

		// miss(1)만 DB에서 product 조회
		Product p1 = product(1L, "P1", 1000L);
		given(productRepository.findByIdInAndIsActiveTrueAndDeletedAtIsNull(eq(List.of(1L))))
			.willReturn(List.of(p1));

		// warmup pipeline 수행
		given(redis.executePipelined(any(RedisCallback.class))).willReturn(List.of());
		given(objectMapper.writeValueAsString(any(ProductSnap.class))).willReturn("NEWJSON");

		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.SEVEN);

		// then
		assertThat(res.getItems()).hasSize(2);
		assertThat(res.getItems().get(0).getProductId()).isEqualTo(2L);
		assertThat(res.getItems().get(0).getSoldQty()).isEqualTo(7L);
		assertThat(res.getItems().get(1).getProductId()).isEqualTo(1L);
		assertThat(res.getItems().get(1).getSoldQty()).isEqualTo(2L);

		then(orderProductRepository).shouldHaveNoInteractions();
		then(productRepository).should().findByIdInAndIsActiveTrueAndDeletedAtIsNull(eq(List.of(1L)));
		then(redis).should().executePipelined(any(RedisCallback.class));
	}

	@Test
	void finalKeyEmpty_butOldKeyHasData_usesOldKey_andDoesNotCallDbAggregation() throws Exception {
		// given
		String finalKey = "rank:zset:30d";
		String oldKey = finalKey + ":old";

		given(zsetOps.reverseRangeWithScores(eq(finalKey), eq(0L), eq(19L))).willReturn(Set.of());

		Set<ZSetOperations.TypedTuple<String>> oldTuples = linkedTuples(
			tuple("202", PopularScoreCodec.encode(40, 202))
		);
		given(zsetOps.reverseRangeWithScores(eq(oldKey), eq(0L), eq(19L))).willReturn(oldTuples);

		// snap miss => null 포함 리스트 필요
		given(valueOps.multiGet(anyList())).willReturn(Collections.singletonList(null));

		Product p202 = product(202L, "B", 2000L);
		given(productRepository.findByIdInAndIsActiveTrueAndDeletedAtIsNull(eq(List.of(202L))))
			.willReturn(List.of(p202));

		given(redis.executePipelined(any(RedisCallback.class))).willReturn(List.of());
		given(objectMapper.writeValueAsString(any(ProductSnap.class))).willReturn("NEW202");

		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.THIRTY);

		// then
		assertThat(res.getItems()).hasSize(1);
		assertThat(res.getItems().get(0).getProductId()).isEqualTo(202L);
		assertThat(res.getItems().get(0).getSoldQty()).isEqualTo(40L);

		// ✅ 집계 DB는 절대 타면 안 됨
		then(orderProductRepository).shouldHaveNoInteractions();

		// ✅ product 상세 조회는 탐
		then(productRepository).should().findByIdInAndIsActiveTrueAndDeletedAtIsNull(eq(List.of(202L)));

		// ✅ multiGet이 요청한 key가 202 포함하는지 확인(키 하드코딩 결합 제거)
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass((Class) List.class);
		then(valueOps).should().multiGet(keysCaptor.capture());
		assertThat(keysCaptor.getValue()).hasSize(1);
		assertThat(keysCaptor.getValue().get(0)).isEqualTo("product:snap:202");
	}

	@Test
	void finalKeyEmpty_andOldKeyEmpty_thenFallbackDbAggregation() {
		// given
		String finalKey = "rank:zset:30d";
		String oldKey = finalKey + ":old";

		given(zsetOps.reverseRangeWithScores(eq(finalKey), eq(0L), eq(19L))).willReturn(Set.of());
		given(zsetOps.reverseRangeWithScores(eq(oldKey), eq(0L), eq(19L))).willReturn(Set.of());

		List<ProductSoldQtyDTO> agg = List.of(
			new ProductSoldQtyDTO(101L, 50L),
			new ProductSoldQtyDTO(202L, 40L)
		);
		given(orderProductRepository.findPopularProduct(any(), any(), eq(OrderStatus.PAID), any(Pageable.class)))
			.willReturn(agg);

		Product p101 = product(101L, "A", 1000L);
		Product p202 = product(202L, "B", 2000L);
		given(productRepository.findByIdInAndIsActiveTrueAndDeletedAtIsNull(eq(List.of(101L, 202L))))
			.willReturn(List.of(p101, p202));

		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.THIRTY);

		// then
		assertThat(res.getItems()).hasSize(2);
		assertThat(res.getItems().get(0).getProductId()).isEqualTo(101L);
		assertThat(res.getItems().get(0).getSoldQty()).isEqualTo(50L);

		// pageSize=20 보장
		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		then(orderProductRepository).should()
			.findPopularProduct(any(LocalDateTime.class), any(LocalDateTime.class), eq(OrderStatus.PAID), pageableCaptor.capture());
		assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);
		assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
	}

	@Test
	void zsetHasOnlyInvalidTuples_thenFallbackDbAggregation() {
		// given
		String key = "rank:zset:7d";
		String oldKey = key + ":old";

		Set<ZSetOperations.TypedTuple<String>> bad = linkedTuples(
			tuple(null, PopularScoreCodec.encode(10, 1)), // value null
			tuple("1", null)                               // score null
		);

		given(zsetOps.reverseRangeWithScores(eq(key), eq(0L), eq(19L))).willReturn(bad);
		given(zsetOps.reverseRangeWithScores(eq(oldKey), eq(0L), eq(19L))).willReturn(Set.of());

		List<ProductSoldQtyDTO> agg = List.of(new ProductSoldQtyDTO(101L, 50L));
		given(orderProductRepository.findPopularProduct(any(), any(), eq(OrderStatus.PAID), any(Pageable.class)))
			.willReturn(agg);

		Product p101 = product(101L, "A", 1000L);
		given(productRepository.findByIdInAndIsActiveTrueAndDeletedAtIsNull(eq(List.of(101L))))
			.willReturn(List.of(p101));

		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.SEVEN);

		// then
		assertThat(res.getItems()).hasSize(1);
		assertThat(res.getItems().get(0).getProductId()).isEqualTo(101L);
		assertThat(res.getItems().get(0).getSoldQty()).isEqualTo(50L);
	}

	@Test
	void zsetHit_butSomeProductsMissingInDb_skipsMissing_andRanksAreCompacted() throws Exception {
		// given
		String key = "rank:zset:7d";

		Set<ZSetOperations.TypedTuple<String>> tuples = linkedTuples(
			tuple("101", PopularScoreCodec.encode(50, 101)),
			tuple("202", PopularScoreCodec.encode(40, 202)),
			tuple("303", PopularScoreCodec.encode(30, 303))
		);
		given(zsetOps.reverseRangeWithScores(eq(key), eq(0L), eq(19L))).willReturn(tuples);

		// 스냅샷 전부 miss (null 포함 => Arrays.asList 써야 함)
		given(valueOps.multiGet(eq(List.of("product:snap:101", "product:snap:202", "product:snap:303"))))
			.willReturn(Arrays.asList(null, null, null));

		// DB에서 202는 빠짐(비활성/삭제)
		Product p101 = product(101L, "A", 1000L);
		Product p303 = product(303L, "C", 3000L);
		given(productRepository.findByIdInAndIsActiveTrueAndDeletedAtIsNull(eq(List.of(101L, 202L, 303L))))
			.willReturn(List.of(p101, p303));

		given(redis.executePipelined(any(RedisCallback.class))).willReturn(List.of());
		given(objectMapper.writeValueAsString(any(ProductSnap.class))).willReturn("NEW");

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

	private ProductSnap snapMock(Long id, String name, Long price) {
		ProductSnap s = mock(ProductSnap.class);
		given(s.getProductId()).willReturn(id);
		given(s.getName()).willReturn(name);
		given(s.getPrice()).willReturn(price);
		return s;
	}

	private ZSetOperations.TypedTuple<String> tuple(String value, Double score) {
		return new org.springframework.data.redis.core.DefaultTypedTuple<>(value, score);
	}

	@SafeVarargs
	private final Set<ZSetOperations.TypedTuple<String>> linkedTuples(ZSetOperations.TypedTuple<String>... tuples) {
		Set<ZSetOperations.TypedTuple<String>> set = new LinkedHashSet<>();
		for (var t : tuples) set.add(t);
		return set;
	}
}
