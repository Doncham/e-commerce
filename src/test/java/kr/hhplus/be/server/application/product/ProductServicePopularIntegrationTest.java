package kr.hhplus.be.server.application.product;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
class ProductServicePopularIntegrationTest {

	@Autowired ProductService productService;

	@Autowired UserRepository userRepository;
	@Autowired AddressRepository addressRepository;
	@Autowired ProductRepository productRepository;
	@Autowired OrderRepository orderRepository;
	@Autowired OrderProductRepository orderProductRepository;

	@Test
	void getPopulars_SEVEN_returnsRankedItems_forPaidOrders() {
		// given: user + address(ShippingInfo)
		User user = userRepository.save(TestFixture.user());
		Address address = addressRepository.save(TestFixture.address(user));
		ShippingInfo shippingInfo = TestFixture.shippingFrom(address);

		// given: products (저장해서 id 확보)
		Product p1 = productRepository.save(TestFixture.product("AAA", 10_000L));
		Product p2 = productRepository.save(TestFixture.product("BBB", 20_000L));
		Product p3 = productRepository.save(TestFixture.product("CCC", 30_000L));

		// given: order1(PAID) - p1 qty 5, p2 qty 1
		Order o1 = orderRepository.save(TestFixture.draftOrder(user, shippingInfo));
		o1.paid();
		orderRepository.save(o1);

		OrderProduct o1p1 = TestFixture.orderProduct(p1.getId(), p1.getName(), p1.getPrice(), 5);
		o1p1.initOrder(o1);
		orderProductRepository.save(o1p1);

		OrderProduct o1p2 = TestFixture.orderProduct(p2.getId(), p2.getName(), p2.getPrice(), 1);
		o1p2.initOrder(o1);
		orderProductRepository.save(o1p2);

		// given: order2(PAID) - p1 qty 2, p3 qty 10
		Order o2 = orderRepository.save(TestFixture.draftOrder(user, shippingInfo));
		o2.paid();
		orderRepository.save(o2);

		OrderProduct o2p1 = TestFixture.orderProduct(p1.getId(), p1.getName(), p1.getPrice(), 2);
		o2p1.initOrder(o2);
		orderProductRepository.save(o2p1);

		OrderProduct o2p3 = TestFixture.orderProduct(p3.getId(), p3.getName(), p3.getPrice(), 10);
		o2p3.initOrder(o2);
		orderProductRepository.save(o2p3);

		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.SEVEN);

		// then: soldQty 합산 기준으로 랭킹 확인
		// p3: 10, p1: 7, p2: 1
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

		// PAID 주문 하나
		Order paid = orderRepository.save(TestFixture.draftOrder(user, shippingInfo));
		paid.paid();
		orderRepository.save(paid);

		OrderProduct paidItem = TestFixture.orderProduct(p1.getId(), p1.getName(), p1.getPrice(), 3);
		paidItem.initOrder(paid);
		orderProductRepository.save(paidItem);

		// NOT PAID 주문 하나 (예: CREATED)
		Order created = orderRepository.save(TestFixture.draftOrder(user, shippingInfo));
		orderRepository.save(created);

		OrderProduct createdItem = TestFixture.orderProduct(p1.getId(), p1.getName(), p1.getPrice(), 999);
		createdItem.initOrder(created);
		orderProductRepository.save(createdItem);

		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.SEVEN);

		// then: NOT PAID는 제외되어 qty=3만 집계
		assertThat(res.getItems()).hasSize(1);
		assertThat(res.getItems().get(0).getProductId()).isEqualTo(p1.getId());
		assertThat(res.getItems().get(0).getSoldQty()).isEqualTo(3);
	}

	/**
	 * 선택: createdAt 범위 제외 테스트를 하고 싶으면 Reflection으로 createdAt을 옮기는 방식.
	 * 프로젝트에서 BaseTimeEntity createdAt이 final/접근 제약이 강하면 실패할 수 있음.
	 */
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

		OrderProduct inItem = TestFixture.orderProduct(p1.getId(), p1.getName(), p1.getPrice(), 2);
		inItem.initOrder(inRange);
		orderProductRepository.save(inItem);

		// 범위 밖 주문
		Order outRange = orderRepository.save(TestFixture.draftOrder(user, shippingInfo));
		outRange.paid();
		forceCreatedAt(outRange, LocalDateTime.now().minusDays(40));
		orderRepository.save(outRange);

		OrderProduct outItem = TestFixture.orderProduct(p1.getId(), p1.getName(), p1.getPrice(), 100);
		outItem.initOrder(outRange);
		orderProductRepository.save(outItem);

		// when
		PopularProductsResponse res = productService.getPopulars(PopularDateRange.SEVEN);

		// then: out-of-range(40일 전)는 제외되어 qty=2만 남아야 함
		assertThat(res.getItems()).hasSize(1);
		assertThat(res.getItems().get(0).getSoldQty()).isEqualTo(2);
	}

	// ===== Helpers =====
	private void forceCreatedAt(Order order, LocalDateTime createdAt) {
		// createdAt 필드명이 BaseTimeEntity에 있을 가능성이 큼.
		// 이름이 "createdAt"이 아니라면 프로젝트 실제 필드명에 맞게 바꿔야 함.
		ReflectionTestUtils.setField(order, "createdAt", createdAt);
	}
}