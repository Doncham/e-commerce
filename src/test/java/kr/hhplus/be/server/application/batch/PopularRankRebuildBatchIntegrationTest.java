package kr.hhplus.be.server.application.batch;

import static org.assertj.core.api.Assertions.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import kr.hhplus.be.server.TestFixture;
import kr.hhplus.be.server.application.FixedClockConfig;
import kr.hhplus.be.server.application.product.batch.PopularRankRebuildService;
import kr.hhplus.be.server.domain.address.Address;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.domain.order.ShippingInfo;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.domain.product.Product;
import kr.hhplus.be.server.domain.user.User;
import kr.hhplus.be.server.infrastructure.persistence.order.OrderRepository;
import kr.hhplus.be.server.infrastructure.persistence.orderproduct.OrderProductRepository;
import kr.hhplus.be.server.infrastructure.persistence.product.ProductRepository;
import kr.hhplus.be.server.infrastructure.persistence.user.UserRepository;

@SpringBootTest
@ActiveProfiles("test")
@Import(FixedClockConfig.class)
class PopularRankRebuildServiceIntegrationTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Autowired PopularRankRebuildService rebuildService;
	@Autowired StringRedisTemplate redis;
	@Autowired Clock clock;

	@Autowired
	ProductRepository productRepository;
	@Autowired
	OrderRepository orderRepository;
	@Autowired
	OrderProductRepository orderProductRepository;
	@Autowired
	UserRepository userRepository;

	@Autowired EntityManager em;

	@AfterEach
	void cleanup() {
		// Redis keys cleanup
		redis.delete("rank:7d");
		redis.delete("rank:30d");
		redis.delete("rank:7d:tmp");
		redis.delete("rank:30d:tmp");
		redis.delete("rank:7d:old");
		redis.delete("rank:30d:old");

		// DB cleanup (연관관계/외래키 순서에 맞춰 필요시 조정)
		orderProductRepository.deleteAllInBatch();
		orderRepository.deleteAllInBatch();
		productRepository.deleteAllInBatch();
		em.clear();
	}

	@Test
	@Transactional
	void rebuild7d_and_30d_should_write_expected_scores_to_redis() {
		// 기준
		LocalDate today = LocalDate.of(2026, 2, 11);
		LocalDateTime to = today.atStartOfDay(); // 2026-02-11 00:00 (KST 의미)
		LocalDateTime in7 = to.minusDays(1).plusHours(3);      // 2/10 03:00 -> 7d/30d 포함
		LocalDateTime in30 = to.minusDays(20).plusHours(10);   // 1/22 10:00 -> 30d 포함, 7d 제외
		LocalDateTime out7 = to.minusDays(8).plusHours(1);     // 2/03 01:00 -> 30d 포함, 7d 제외

		// --- users ---
		User user = userRepository.save(TestFixture.user());

		// --- shippingInfo ---
		Address address = TestFixture.address(user);
		ShippingInfo shippingInfo = TestFixture.shippingFrom(address);

		// --- products ---
		Product p1 = productRepository.save(TestFixture.product("p1", 10_000L));
		Product p2 = productRepository.save(TestFixture.product("p2", 10_000L));
		Product p3 = productRepository.save(TestFixture.product("p3", 10_000L));

		// p3는 deletedAt != null -> 집계 제외
		ReflectionTestUtils.setField(p3, "deletedAt", LocalDateTime.now());
		// isActive = true는 기본값이 true라고 가정. 아니라면 여기서 true로 세팅.
		// ReflectionTestUtils.setField(p3, "isActive", true);
		productRepository.saveAndFlush(p3);

		// --- orders ---
		// TestFixture.createdOrder를 쓰고 싶지만 createdAt/status 세팅이 필요하니
		Order o1 = orderRepository.saveAndFlush(makePaidOrderAt(user, shippingInfo));
		Order o2 = orderRepository.saveAndFlush(makePaidOrderAt(user, shippingInfo));
		Order o3 = orderRepository.saveAndFlush(makePaidOrderAt(user, shippingInfo));
		Order o4 = orderRepository.saveAndFlush(makeFailedOrderAt(user, shippingInfo)); // status 제외
		setCreatedAt(in7, o1);
		setCreatedAt(in30, o2);
		setCreatedAt(out7, o3);
		setCreatedAt(in7, o4);

		// --- order_products ---
		// p1: o1에서 2, o2에서 3, o3(out7)에서 5
		saveOrderProduct(o1, p1, 2);
		saveOrderProduct(o2, p1, 3);
		saveOrderProduct(o3, p1, 5);

		// p2: o1에서 7
		saveOrderProduct(o1, p2, 7);

		// p3(deleted): o1에서 100 (하지만 제외되어야 함)
		saveOrderProduct(o1, p3, 100);

		// FAILED order: 제외되어야 함
		saveOrderProduct(o4, p1, 999);

		em.flush();
		em.clear();

		// --- when ---
		rebuildService.rebuild7d(50);
		rebuildService.rebuild30d(50);

		// --- then: 7d ---
		Double p1_7d = redis.opsForZSet().score("rank:7d", String.valueOf(p1.getId()));
		Double p2_7d = redis.opsForZSet().score("rank:7d", String.valueOf(p2.getId()));
		Double p3_7d = redis.opsForZSet().score("rank:7d", String.valueOf(p3.getId()));
		System.out.println("null임?" + p1_7d);

		// 7d에서는 out7(8일 전) 제외 => p1=2, p2=7
		assertThat(p1_7d).isEqualTo(2.0);
		assertThat(p2_7d).isEqualTo(7.0);
		assertThat(p3_7d).isNull();

		// --- then: 30d ---
		Double p1_30d = redis.opsForZSet().score("rank:30d", String.valueOf(p1.getId()));
		Double p2_30d = redis.opsForZSet().score("rank:30d", String.valueOf(p2.getId()));
		Double p3_30d = redis.opsForZSet().score("rank:30d", String.valueOf(p3.getId()));

		// 30d에서는 in7(2) + in30(3) + out7(5) 포함, FAILED 제외, deleted 제외 => p1=2+3+5=10
		assertThat(p1_30d).isEqualTo(10.0);
		assertThat(p2_30d).isEqualTo(7.0);
		assertThat(p3_30d).isNull();
	}

	private Order makePaidOrderAt(User user, ShippingInfo shippingInfo) {
		Order o = TestFixture.draftOrder(user, shippingInfo);
		ReflectionTestUtils.setField(o, "status", OrderStatus.PAID);
		return o;
	}

	private Order makeFailedOrderAt(User user, ShippingInfo shippingInfo) {
		Order o = TestFixture.draftOrder(user,shippingInfo);
		ReflectionTestUtils.setField(o, "status", OrderStatus.FAILED);
		return o;
	}

	private void saveOrderProduct(Order order, Product product, long qty) {
		// 네 도메인의 OrderProduct 생성 시그니처에 맞춰 수정 필요
		// (너 fixture의 orderProduct(product, qty)는 productId를 넣는데 product는 저장됐으니 id 있음)
		OrderProduct op = TestFixture.orderProduct(product, qty);

		// OrderProduct가 order와 연관관계로 묶여야 join op.order o가 먹음
		// 즉, op에 order를 세팅해야 함(필드명이 order라고 가정)
		ReflectionTestUtils.setField(op, "order", order);

		orderProductRepository.save(op);
	}

	private void setCreatedAt(LocalDateTime createdAt, Order o) {
		ReflectionTestUtils.setField(o, "createdAt", createdAt);
	}
}