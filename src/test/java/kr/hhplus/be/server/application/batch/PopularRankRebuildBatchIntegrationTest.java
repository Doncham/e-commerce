package kr.hhplus.be.server.application.batch;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.persistence.EntityManager;
import kr.hhplus.be.server.TestFixture;
import kr.hhplus.be.server.application.FixedClockConfig;
import kr.hhplus.be.server.application.product.batch.DailyProductSalesRebuildService;
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

	@Autowired
	DailyProductSalesRebuildService rebuildService;
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

	private void saveOrderProduct(Order order, Product product) {
		// 네 도메인의 OrderProduct 생성 시그니처에 맞춰 수정 필요
		// (너 fixture의 orderProduct(product, qty)는 productId를 넣는데 product는 저장됐으니 id 있음)
		OrderProduct op = TestFixture.orderProduct(product);

		// OrderProduct가 order와 연관관계로 묶여야 join op.order o가 먹음
		// 즉, op에 order를 세팅해야 함(필드명이 order라고 가정)
		ReflectionTestUtils.setField(op, "order", order);

		orderProductRepository.save(op);
	}

	private void setCreatedAt(LocalDateTime createdAt, Order o) {
		ReflectionTestUtils.setField(o, "createdAt", createdAt);
	}
}