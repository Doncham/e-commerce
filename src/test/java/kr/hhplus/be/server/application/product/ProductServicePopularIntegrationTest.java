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

import jakarta.persistence.EntityManager;
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
@ActiveProfiles("test")
@Transactional
class ProductServicePopularDbIntegrationTest {

	@Autowired ProductService productService;

	@Autowired UserRepository userRepository;
	@Autowired AddressRepository addressRepository;
	@Autowired ProductRepository productRepository;
	@Autowired OrderRepository orderRepository;
	@Autowired OrderProductRepository orderProductRepository;

	@Autowired
	EntityManager em;

	// ✅ 캐시/락을 "항상 DB 조회 경로로 강제"
	// @MockitoBean StringRedisTemplate stringRedisTemplate;
	// @MockitoBean ValueOperations<String, String> valueOps;
	// @MockitoBean RedissonClient redissonClient;
	// @MockitoBean RLock rLock;

	@BeforeEach
	void forceDbPath() throws Exception {

	}

	@Test
	void sevenDays_ranksBySoldQty_onlyPaidOrders() {
		// given
		Given given = new Given().userAndShipping();

		Product p1 = given.product("AAA", 10_000L);
		Product p2 = given.product("BBB", 20_000L);
		Product p3 = given.product("CCC", 30_000L);

		// o1: p1(5), p2(1)
		Order o1 = given.paidOrderAt(LocalDateTime.now().minusDays(1));
		given.orderItem(o1, p1, 5);
		given.orderItem(o1, p2, 1);

		// o2: p1(2), p3(10)
		Order o2 = given.paidOrderAt(LocalDateTime.now().minusDays(2));
		given.orderItem(o2, p1, 2);
		given.orderItem(o2, p3, 10);

		em.flush();
		em.clear();

		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.SEVEN);

		// then: p3(10), p1(7), p2(1)
		assertThat(res.getItems()).extracting("productId").containsExactly(p3.getId(), p1.getId(), p2.getId());
		assertThat(res.getItems()).extracting("soldQty").containsExactly(10L, 7L, 1L);
		assertThat(res.getItems()).extracting("rank").containsExactly(1, 2, 3);
		assertThat(res.getRange()).isEqualTo("7d");
		assertThat(res.getGeneratedAt()).isNotNull();
	}

	@Test
	void excludesNotPaidOrders() {
		// given
		Given given = new Given().userAndShipping();
		Product p1 = given.product("AAA", 10_000L);

		Order paid = given.paidOrderAt(LocalDateTime.now().minusDays(1));
		given.orderItem(paid, p1, 3);

		Order notPaid = given.notPaidOrderAt(LocalDateTime.now().minusDays(1));
		given.orderItem(notPaid, p1, 999);

		em.flush();
		em.clear();

		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.SEVEN);

		// then
		assertThat(res.getItems()).hasSize(1);
		assertThat(res.getItems().get(0).getProductId()).isEqualTo(p1.getId());
		assertThat(res.getItems().get(0).getSoldQty()).isEqualTo(3L);
	}

	@Test
	void excludesOutOfRangeOrders() {
		// given
		Given given = new Given().userAndShipping();
		Product p1 = given.product("AAA", 10_000L);

		Order inRange = given.paidOrderAt(LocalDateTime.now().minusDays(1));
		given.orderItem(inRange, p1, 2);

		// 40일 전 => 7d 범위 밖
		Order outRange = given.paidOrderAt(LocalDateTime.now().minusDays(40));
		given.orderItem(outRange, p1, 100);

		em.flush();
		em.clear();

		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.SEVEN);

		// then
		assertThat(res.getItems()).hasSize(1);
		assertThat(res.getItems().get(0).getProductId()).isEqualTo(p1.getId());
		assertThat(res.getItems().get(0).getSoldQty()).isEqualTo(2L);
	}

	// =========================
	// Given DSL (테스트 데이터 빌더)
	// =========================
	private class Given {
		private User user;
		private ShippingInfo shipping;

		Given userAndShipping() {
			this.user = userRepository.save(TestFixture.user());
			Address address = addressRepository.save(TestFixture.address(user));
			this.shipping = TestFixture.shippingFrom(address);
			return this;
		}

		Product product(String name, long price) {
			return productRepository.save(TestFixture.product(name, price));
		}

		Order paidOrderAt(LocalDateTime createdAt) {
			Order o = orderRepository.save(TestFixture.draftOrder(user, shipping));
			// 상태 변경
			o.paid();

			// ✅ createdAt을 원하는 값으로 강제
			forceCreatedAtManagedEntity(o, createdAt);

			return o;
		}

		Order notPaidOrderAt(LocalDateTime createdAt) {
			Order o = orderRepository.save(TestFixture.draftOrder(user, shipping));
			forceCreatedAtManagedEntity(o, createdAt);
			return o;
		}

		void orderItem(Order order, Product p, long qty) {
			OrderProduct op = TestFixture.orderProduct(p.getId(), p.getName(), p.getPrice(), qty);
			op.initOrder(order);
			orderProductRepository.save(op);
		}
	}

	/**
	 * ✅ "저장 후" 관리 엔티티 상태에서 createdAt을 바꾸고,
	 * flush 타이밍에 UPDATE가 나가도록 한다.
	 *
	 * 주의: @CreatedDate auditing이 PrePersist에서 덮는 경우가 있어
	 * 저장 전에 set하는 방식보다 "저장 후 변경"이 안전하다.
	 */
	private void forceCreatedAtManagedEntity(Order order, LocalDateTime createdAt) {
		ReflectionTestUtils.setField(order, "createdAt", createdAt);
		ReflectionTestUtils.setField(order, "updatedAt", createdAt);
	}
}