package kr.hhplus.be.server.application.product;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.TestFixture;
import kr.hhplus.be.server.api.product.response.PopularProductsResponse;
import kr.hhplus.be.server.domain.address.Address;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.ShippingInfo;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.domain.product.Product;
import kr.hhplus.be.server.domain.user.User;
import kr.hhplus.be.server.infrastructure.persistence.address.AddressRepository;
import kr.hhplus.be.server.infrastructure.persistence.order.OrderRepository;
import kr.hhplus.be.server.infrastructure.persistence.orderproduct.OrderProductRepository;
import kr.hhplus.be.server.infrastructure.persistence.product.ProductRepository;
import kr.hhplus.be.server.infrastructure.persistence.user.UserRepository;

@SpringBootTest
@Transactional
@ActiveProfiles("test")
class ProductServicePopularIntegrationTest {

	@Autowired ProductService productService;
	@Autowired UserRepository userRepository;
	@Autowired AddressRepository addressRepository;
	@Autowired ProductRepository productRepository;
	@Autowired OrderRepository orderRepository;
	@Autowired OrderProductRepository orderProductRepository;
	@Autowired StringRedisTemplate redisTemplate;

	// ✅ Redis/Redisson 의존 제거 (DB 통합 테스트 안정화)
	@MockitoBean
	StringRedisTemplate stringRedisTemplate;
	@MockitoBean
	RedissonClient redissonClient;
	// ✅ getPopulars 내부에서 필요한 하위 mock들
	@MockitoBean
	RLock rLock;
	@MockitoBean
	ValueOperations<String, String> valueOps;
	@BeforeEach
	void setUpCacheAndLockMocks() throws Exception {
		// 캐시: 항상 miss
		given(stringRedisTemplate.opsForValue()).willReturn(valueOps);
		given(valueOps.get(anyString())).willReturn(null);
		// set은 호출돼도 무시
		willDoNothing().given(valueOps).set(anyString(), anyString(), any());
		// 락: 항상 획득 성공 (락 경합 없이 DB 조회 흐름 타게)
		given(redissonClient.getLock(anyString())).willReturn(rLock);
		given(rLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
		willDoNothing().given(rLock).unlock();
		System.out.println("log: " + stringRedisTemplate.getClass());
	}


	@Test
	void getPopulars_SEVEN_returnsRankedItems_forPaidOrders() {
		// given: user + address(ShippingInfo)
		User user = userRepository.save(TestFixture.user());
		Address address = addressRepository.save(TestFixture.address(user));
		ShippingInfo shippingInfo = TestFixture.shippingFrom(address);

		// given: products
		Product p1 = productRepository.save(TestFixture.product("AAA", 10_000L));
		Product p2 = productRepository.save(TestFixture.product("BBB", 20_000L));
		Product p3 = productRepository.save(TestFixture.product("CCC", 30_000L));

		// order1(PAID) - p1 qty 5, p2 qty 1
		Order o1 = orderRepository.save(TestFixture.draftOrder(user, shippingInfo));
		o1.paid();
		orderRepository.save(o1);

		orderProductRepository.save(withOrder(TestFixture.orderProduct(p1.getId(), p1.getName(), p1.getPrice(), 5), o1));
		orderProductRepository.save(withOrder(TestFixture.orderProduct(p2.getId(), p2.getName(), p2.getPrice(), 1), o1));

		// order2(PAID) - p1 qty 2, p3 qty 10
		Order o2 = orderRepository.save(TestFixture.draftOrder(user, shippingInfo));
		o2.paid();
		orderRepository.save(o2);

		orderProductRepository.save(withOrder(TestFixture.orderProduct(p1.getId(), p1.getName(), p1.getPrice(), 2), o2));
		orderProductRepository.save(withOrder(TestFixture.orderProduct(p3.getId(), p3.getName(), p3.getPrice(), 10), o2));

		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.SEVEN);

		// then: p3(10), p1(7), p2(1)
		assertThat(res).isNotNull();
		assertThat(res.getItems()).hasSize(3);

		assertThat(res.getItems().get(0).getProductId()).isEqualTo(p3.getId());
		assertThat(res.getItems().get(0).getSoldQty()).isEqualTo(10);
		assertThat(res.getItems().get(0).getRank()).isEqualTo(1);

		assertThat(res.getItems().get(1).getProductId()).isEqualTo(p1.getId());
		assertThat(res.getItems().get(1).getSoldQty()).isEqualTo(7);
		assertThat(res.getItems().get(1).getRank()).isEqualTo(2);

		assertThat(res.getItems().get(2).getProductId()).isEqualTo(p2.getId());
		assertThat(res.getItems().get(2).getSoldQty()).isEqualTo(1);
		assertThat(res.getItems().get(2).getRank()).isEqualTo(3);

		assertThat(res.getRange()).isEqualTo("7d");
		assertThat(res.getGeneratedAt()).isNotNull();
	}

	@Test
	void getPopulars_excludesNotPaidOrders() {
		// given
		User user = userRepository.save(TestFixture.user());
		Address address = addressRepository.save(TestFixture.address(user));
		ShippingInfo shippingInfo = TestFixture.shippingFrom(address);

		Product p1 = productRepository.save(TestFixture.product("AAA", 10_000L));

		// PAID 주문
		Order paid = orderRepository.save(TestFixture.draftOrder(user, shippingInfo));
		paid.paid();
		orderRepository.save(paid);

		orderProductRepository.save(withOrder(TestFixture.orderProduct(p1.getId(), p1.getName(), p1.getPrice(), 3), paid));

		// NOT PAID(=DRAFT/CREATED) 주문
		Order created = orderRepository.save(TestFixture.draftOrder(user, shippingInfo));
		orderRepository.save(created);

		orderProductRepository.save(withOrder(TestFixture.orderProduct(p1.getId(), p1.getName(), p1.getPrice(), 999), created));

		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.SEVEN);

		// then: PAID(3)만 반영
		assertThat(res.getItems()).hasSize(1);
		assertThat(res.getItems().get(0).getProductId()).isEqualTo(p1.getId());
		assertThat(res.getItems().get(0).getSoldQty()).isEqualTo(3);
	}

	@Test
	void getPopulars_excludesOrdersOutOfRange_whenCreatedAtManipulated() {
		// given
		User user = userRepository.save(TestFixture.user());
		Address address = addressRepository.save(TestFixture.address(user));
		ShippingInfo shippingInfo = TestFixture.shippingFrom(address);

		Product p1 = productRepository.save(TestFixture.product("AAA", 10_000L));

		// 범위 내 주문
		Order inRange = orderRepository.save(TestFixture.draftOrder(user, shippingInfo));
		inRange.paid();
		forceCreatedAt(inRange, LocalDateTime.now().minusDays(1));
		orderRepository.save(inRange);

		orderProductRepository.save(withOrder(TestFixture.orderProduct(p1.getId(), p1.getName(), p1.getPrice(), 2), inRange));

		// 범위 밖 주문(40일 전)
		Order outRange = orderRepository.save(TestFixture.draftOrder(user, shippingInfo));
		outRange.paid();
		forceCreatedAt(outRange, LocalDateTime.now().minusDays(40));
		orderRepository.save(outRange);

		orderProductRepository.save(withOrder(TestFixture.orderProduct(p1.getId(), p1.getName(), p1.getPrice(), 100), outRange));

		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.SEVEN);

		// then: qty=2만
		assertThat(res.getItems()).hasSize(1);
		assertThat(res.getItems().get(0).getSoldQty()).isEqualTo(2);
	}

	// ===== Helpers =====
	private OrderProduct withOrder(OrderProduct op, Order order) {
		op.initOrder(order);
		return op;
	}

	private void forceCreatedAt(Order order, LocalDateTime createdAt) {
		ReflectionTestUtils.setField(order, "createdAt", createdAt);
		ReflectionTestUtils.setField(order, "updatedAt", createdAt);
	}
}