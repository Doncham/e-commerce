package kr.hhplus.be.server.application.product;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.api.product.response.PopularProductsResponse;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.domain.product.Product;
import kr.hhplus.be.server.infrastructure.persistence.inventory.InventoryRepository;
import kr.hhplus.be.server.infrastructure.persistence.orderproduct.OrderProductRepository;
import kr.hhplus.be.server.infrastructure.persistence.product.ProductRepository;

@ExtendWith(MockitoExtension.class)
class PopularProductReadTest {

	@Mock InventoryRepository inventoryRepository; // getPopulars에는 사용 안 함
	@Mock OrderProductRepository orderProductRepository;
	@Mock ProductRepository productRepository;

	@Mock StringRedisTemplate stringRedisTemplate;
	@Mock ValueOperations<String, String> valueOps;

	@Mock ObjectMapper objectMapper;

	@Mock RedissonClient redissonClient;
	@Mock RLock lock;

	@InjectMocks ProductService productService;


	@Test
	void getPopulars_cacheHit_returnsCached_withoutDbCall() throws Exception {
		// given
		String cacheKey = "popular:SEVEN";
		String cachedJson = "{\"range\":\"7d\",\"generatedAt\":\"2026-02-04T00:00:00\",\"items\":[]}";
		PopularProductsResponse cachedObj = new PopularProductsResponse("7d", LocalDateTime.now(), List.of());

		// StringRedisTemplate.opsForValue() 기본 stub
		given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
		given(valueOps.get(cacheKey)).willReturn(cachedJson);
		given(objectMapper.readValue(eq(cachedJson), eq(PopularProductsResponse.class))).willReturn(cachedObj);

		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.SEVEN);

		// then
		assertThat(res).isSameAs(cachedObj);

		then(orderProductRepository).shouldHaveNoInteractions();
		then(productRepository).shouldHaveNoInteractions();
		then(redissonClient).shouldHaveNoInteractions(); // 캐시 hit이면 락도 안 잡음
	}

	@Test
	void getPopulars_cacheMiss_lockAcquired_doubleCheckHit_returnsCache_withoutDbCall() throws Exception {
		// given: 1차 캐시 miss, 락 획득, 2차 캐시 hit
		String cacheKey = "popular:SEVEN";
		String cachedJson2 = "{\"range\":\"7d\",\"generatedAt\":\"2026-02-04T00:00:00\",\"items\":[]}";
		PopularProductsResponse cachedObj2 = new PopularProductsResponse("7d", LocalDateTime.now(), List.of());

		// StringRedisTemplate.opsForValue() 기본 stub
		given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
		// redissonClient.getLock() 기본 stub
		given(redissonClient.getLock(anyString())).willReturn(lock);

		// 1차 miss
		given(valueOps.get(cacheKey)).willReturn(null);
		// 락 획득 성공
		given(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
		// 2차 캐시 hit (더블체크)
		given(valueOps.get(cacheKey)).willReturn(null, cachedJson2);
		given(objectMapper.readValue(eq(cachedJson2), eq(PopularProductsResponse.class))).willReturn(cachedObj2);


		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.SEVEN);

		// then
		assertThat(res).isSameAs(cachedObj2);

		then(orderProductRepository).shouldHaveNoInteractions();
		then(productRepository).shouldHaveNoInteractions();
		then(lock).should().unlock();
	}

	@Test
	void getPopulars_cacheMiss_lockAcquired_queryDb_setsCache_andReturnsFresh() throws Exception {
		// given: 캐시 miss, 락 성공, 더블체크도 miss => DB 조회 후 setCache
		String cacheKey = "popular:SEVEN";

		// StringRedisTemplate.opsForValue() 기본 stub
		given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
		// redissonClient.getLock() 기본 stub
		given(redissonClient.getLock(anyString())).willReturn(lock);
		// 캐시 2번 조회 모두 miss
		given(valueOps.get(cacheKey)).willReturn(null, null);

		// 락 획득 성공
		given(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);

		// DB 집계 결과
		List<ProductSoldQtyDTO> agg = List.of(
			new ProductSoldQtyDTO(101L, 50L),
			new ProductSoldQtyDTO(202L, 40L),
			new ProductSoldQtyDTO(303L, 30L)
		);

		given(orderProductRepository.findPopularProduct(any(), any(), eq(OrderStatus.PAID), any(Pageable.class)))
			.willReturn(agg);

		// product 202는 누락(비활성화/삭제)
		Product p101 = product(101L, "A", 1000L);
		Product p303 = product(303L, "C", 3000L);

		given(productRepository.findByIdInAndIsActiveTrueAndDeletedAtIsNull(List.of(101L, 202L, 303L)))
			.willReturn(List.of(p101, p303));

		// setCache 시 직렬화될 json
		// ObjectMapper가 writeValueAsString 호출될 테니 stub 필요
		given(objectMapper.writeValueAsString(any(PopularProductsResponse.class)))
			.willReturn("{\"mock\":\"json\"}");

		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.SEVEN);

		// then: 결과 매핑/랭킹 확인 (중간 202 스킵)
		assertThat(res).isNotNull();
		assertThat(res.getItems()).hasSize(2);

		assertThat(res.getItems().get(0).getRank()).isEqualTo(1);
		assertThat(res.getItems().get(0).getProductId()).isEqualTo(101L);
		assertThat(res.getItems().get(0).getSoldQty()).isEqualTo(50L);

		assertThat(res.getItems().get(1).getRank()).isEqualTo(2);
		assertThat(res.getItems().get(1).getProductId()).isEqualTo(303L);
		assertThat(res.getItems().get(1).getSoldQty()).isEqualTo(30L);

		// DB 호출 파라미터 기본 검증
		ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
		then(orderProductRepository).should()
			.findPopularProduct(any(LocalDateTime.class), any(LocalDateTime.class), eq(OrderStatus.PAID), pageableCaptor.capture());

		assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);
		assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);

		// 캐시 저장 호출 검증 (TTL까지)
		then(valueOps).should()
			.set(eq(cacheKey), eq("{\"mock\":\"json\"}"), any(Duration.class));

		then(lock).should().unlock();
	}

	@Test
	void getPopulars_cacheMiss_lockNotAcquired_thenFallbackDbQuery() throws InterruptedException {
		// given: 캐시 miss, 락 실패, (재시도에서도 캐시 없음) => DB fallback
		String cacheKey = "popular:SEVEN";

		// StringRedisTemplate.opsForValue() 기본 stub
		given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
		// redissonClient.getLock() 기본 stub
		given(redissonClient.getLock(anyString())).willReturn(lock);

		// 최초 miss + (재시도에서도 계속 miss) - getCache가 여러번 호출될 수 있으니 넉넉히 null
		given(valueOps.get(cacheKey)).willReturn(null);

		// 락 획득 실패
		given(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(false);

		// DB 집계 결과는 empty
		given(orderProductRepository.findPopularProduct(any(), any(), eq(OrderStatus.PAID), any(Pageable.class)))
			.willReturn(List.of());

		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.SEVEN);

		// then
		assertThat(res).isNotNull();
		assertThat(res.getItems()).isEmpty();

		// 락 실패면 unlock 호출하면 안 됨
		then(lock).should(never()).unlock();
	}

	private Product product(Long id, String name, Long price) {
		Product p = Product.createProduct(name, "desc", price);
		ReflectionTestUtils.setField(p, "id", id);
		return p;
	}
}
