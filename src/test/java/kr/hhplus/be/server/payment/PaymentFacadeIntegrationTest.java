package kr.hhplus.be.server.payment;

import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.FixturePersist;
import kr.hhplus.be.server.TestFixture;
import kr.hhplus.be.server.api.order.request.OrderDraftCreateRequest;
import kr.hhplus.be.server.api.payment.request.PayRequest;
import kr.hhplus.be.server.api.point.request.PointChargeRequest;
import kr.hhplus.be.server.application.outbox.OutboxProcessor;
import kr.hhplus.be.server.domain.address.Address;
import kr.hhplus.be.server.domain.cart.Cart;
import kr.hhplus.be.server.domain.cartItem.CartItem;
import kr.hhplus.be.server.domain.inventory.Inventory;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.domain.outbox.AggregateType;
import kr.hhplus.be.server.domain.outbox.EventType;
import kr.hhplus.be.server.domain.outbox.OutboxEvent;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import kr.hhplus.be.server.domain.payment.PaymentStatus;
import kr.hhplus.be.server.domain.point.Point;
import kr.hhplus.be.server.domain.product.Product;
import kr.hhplus.be.server.domain.user.User;
import kr.hhplus.be.server.infrastructure.persistence.address.AddressRepository;
import kr.hhplus.be.server.infrastructure.persistence.cart.CartRepository;
import kr.hhplus.be.server.infrastructure.persistence.cartItem.CartItemRepository;
import kr.hhplus.be.server.infrastructure.persistence.inventory.InventoryRepository;
import kr.hhplus.be.server.infrastructure.persistence.order.OrderRepository;
import kr.hhplus.be.server.infrastructure.persistence.outbox.OutboxEventRepository;
import kr.hhplus.be.server.infrastructure.persistence.point.PointRepository;
import kr.hhplus.be.server.infrastructure.persistence.product.ProductRepository;
import kr.hhplus.be.server.infrastructure.persistence.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
public class PaymentFacadeIntegrationTest {
	@Autowired
	private MockMvc mockMvc;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private OrderRepository orderRepository;
	@Autowired
	private ProductRepository productRepository;
	@Autowired
	private InventoryRepository inventoryRepository;
	@Autowired
	private AddressRepository addressRepository;
	@Autowired
	private FixturePersist persist;
	@Autowired
	private CartRepository cartRepository;
	@Autowired
	private CartItemRepository cartItemRepository;
	@Autowired
	private PointRepository pointRepository;
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private OutboxProcessor outboxProcessor;
	@Autowired
	private OutboxEventRepository outboxEventRepository;


	@Test
	public void givenPointAndOrderReady_whenPaymentComplete_thenPointAndInventoryReleaseAndOutboxInsertOccur() throws Exception {
		User u = persist.saveAndFlush(userRepository, TestFixture.user());
		Cart cart = persist.saveAndFlush(cartRepository, TestFixture.cart(u));
		Product p = persist.save(productRepository, TestFixture.product("콜라", 2000L));
		CartItem cartItem1 = persist.save(cartItemRepository, TestFixture.cartItem(cart, p, 2));
		Inventory inv = persist.save(inventoryRepository, TestFixture.inventory(p, 10L));
		Address addr = persist.saveAndFlush(addressRepository, TestFixture.address(u));

		// 포인트 충전 api 호출 후 point 잔액 확인
		PointChargeRequest request = PointChargeRequest.builder()
			.userId(u.getId())
			.amount(5000L)
			.idempotencyKey(TestFixture.idemKey())
			.build();
		String json = objectMapper.writeValueAsString(request);

		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/points/charge")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json)
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.chargedAmount").value(5000L))
			.andExpect(jsonPath("$.balanceAfterChange").value(5000L))
			.andDo(print());

		// 주문 api 호출 후 잔액 확인(point의 reserved 필드 변한거 검증하면 될듯)
		OrderDraftCreateRequest orderRequest = OrderDraftCreateRequest.builder()
			.cartId(cart.getId())
			.addressId(addr.getId())
			.memo("빠른 배송 부탁")
			.userId(u.getId())
			.couponId(null)
			.pointUseAmount(3000L)
			.idempotencyKey(TestFixture.idemKey())
			.build();

		String orderJson = objectMapper.writeValueAsString(orderRequest);
		// 주문
		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/order/create")
				.contentType(MediaType.APPLICATION_JSON)
				.content(orderJson)
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.itemTotal").value(4000L))
			.andExpect(jsonPath("$.payAmount").value(1000L))
			.andDo(print());

		Point point = pointRepository.findByUserId(u.getId()).get();
		Long reserved = point.getReserved();
		Inventory inventoryAfterOrder = inventoryRepository.findByProductId(p.getId()).get();
		Order order = orderRepository.findByUserIdAndIdempotencyKey(u.getId(), orderRequest.getIdempotencyKey()).get();
		Assertions.assertEquals(OrderStatus.CREATED, order.getStatus());
		Assertions.assertEquals(3000L, reserved);
		Assertions.assertEquals(inventoryAfterOrder.getReserved(), 2L);

		PayRequest payRequest = PayRequest.of(TestFixture.idemKey(), order.getId());
		String payCreateReq = objectMapper.writeValueAsString(payRequest);

		// 결제
		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/payment/create")
				.contentType(MediaType.APPLICATION_JSON)
				.content(payCreateReq)
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount").value(1000L))
			.andExpect(jsonPath("$.orderId").value(order.getId()))
			.andExpect(jsonPath("$.status").value(PaymentStatus.SUCCESS.toString()))
			.andDo(print());

		Point pointAfterPayment = pointRepository.findByUserId(u.getId()).get();
		Inventory inventoryAfterPayment = inventoryRepository.findByProductId(p.getId()).get();
		Order orderAfterPayment = orderRepository.findByUserIdAndIdempotencyKey(u.getId(), orderRequest.getIdempotencyKey()).get();
		OutboxEvent outboxEvent = outboxEventRepository.findByAggregateIdAndAggregateTypeAndEventType(
			order.getId(),
			AggregateType.ORDER,
			EventType.PAYMENT_COMPLETION_GIVE_POINT
		).get();

		Assertions.assertEquals(2000L, pointAfterPayment.getBalance());
		Assertions.assertEquals(8L, inventoryAfterPayment.getStock());
		Assertions.assertEquals(0L, inventoryAfterPayment.getReserved());
		Assertions.assertEquals(OrderStatus.PAID.toString(), orderAfterPayment.getStatus().toString());

		Assertions.assertNotNull(outboxEvent);
		Assertions.assertEquals(order.getId(), outboxEvent.getAggregateId());
		Assertions.assertEquals(OutboxStatus.PENDING.toString(), outboxEvent.getStatus().toString());
	}

	@Test
	public void givenPointAndOrderPaymentReady_whenOutboxProcessorWork_thenEventStatusChange() throws Exception {
		User u = persist.saveAndFlush(userRepository, TestFixture.user());
		Cart cart = persist.saveAndFlush(cartRepository, TestFixture.cart(u));
		Product p = persist.save(productRepository, TestFixture.product("콜라", 2000L));
		CartItem cartItem1 = persist.save(cartItemRepository, TestFixture.cartItem(cart, p, 2));
		Inventory inv = persist.save(inventoryRepository, TestFixture.inventory(p, 10L));
		Address addr = persist.saveAndFlush(addressRepository, TestFixture.address(u));

		// 포인트 충전 api 호출 후 point 잔액 확인
		PointChargeRequest request = PointChargeRequest.builder()
			.userId(u.getId())
			.amount(5000L)
			.idempotencyKey(TestFixture.idemKey())
			.build();
		String json = objectMapper.writeValueAsString(request);

		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/points/charge")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json)
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.chargedAmount").value(5000L))
			.andExpect(jsonPath("$.balanceAfterChange").value(5000L))
			.andDo(print());

		// 주문 api 호출 후 잔액 확인(point의 reserved 필드 변한거 검증하면 될듯)
		OrderDraftCreateRequest orderRequest = OrderDraftCreateRequest.builder()
			.cartId(cart.getId())
			.addressId(addr.getId())
			.memo("빠른 배송 부탁")
			.userId(u.getId())
			.couponId(null)
			.pointUseAmount(3000L)
			.idempotencyKey(TestFixture.idemKey())
			.build();

		String orderJson = objectMapper.writeValueAsString(orderRequest);
		// 주문
		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/order/create")
				.contentType(MediaType.APPLICATION_JSON)
				.content(orderJson)
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.itemTotal").value(4000L))
			.andExpect(jsonPath("$.payAmount").value(1000L))
			.andDo(print());

		Order order = orderRepository.findByUserIdAndIdempotencyKey(u.getId(), orderRequest.getIdempotencyKey()).get();

		PayRequest payRequest = PayRequest.of(TestFixture.idemKey(), order.getId());
		String payCreateReq = objectMapper.writeValueAsString(payRequest);

		// 결제
		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/payment/create")
				.contentType(MediaType.APPLICATION_JSON)
				.content(payCreateReq)
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount").value(1000L))
			.andExpect(jsonPath("$.orderId").value(order.getId()))
			.andExpect(jsonPath("$.status").value(PaymentStatus.SUCCESS.toString()))
			.andDo(print());


		OutboxEvent outboxEvent = outboxEventRepository.findByAggregateIdAndAggregateTypeAndEventType(
			order.getId(),
			AggregateType.ORDER,
			EventType.PAYMENT_COMPLETION_GIVE_POINT
		).get();

		Assertions.assertNotNull(outboxEvent);
		Assertions.assertEquals(order.getId(), outboxEvent.getAggregateId());
		Assertions.assertEquals(OutboxStatus.PENDING.toString(), outboxEvent.getStatus().toString());

		outboxProcessor.processOutbox();

		OutboxEvent outboxEventAfterProcessor = outboxEventRepository.findByAggregateIdAndAggregateTypeAndEventType(
			order.getId(),
			AggregateType.ORDER,
			EventType.PAYMENT_COMPLETION_GIVE_POINT
		).get();

		Assertions.assertEquals(OutboxStatus.PROCESSED.toString(), outboxEventAfterProcessor.getStatus().toString());
	}

	@Test
	public void givenPointAndOrderPaymentReadyAndOutboxProcessorWork_whenErrorOccur_thenOutboxProcessorProcessFallback() throws Exception {
		User u = persist.saveAndFlush(userRepository, TestFixture.user());
		Cart cart = persist.saveAndFlush(cartRepository, TestFixture.cart(u));
		Product p = persist.save(productRepository, TestFixture.product("콜라", 2000L));
		CartItem cartItem1 = persist.save(cartItemRepository, TestFixture.cartItem(cart, p, 2));
		Inventory inv = persist.save(inventoryRepository, TestFixture.inventory(p, 10L));
		Address addr = persist.saveAndFlush(addressRepository, TestFixture.address(u));

		// 포인트 충전 api 호출 후 point 잔액 확인
		PointChargeRequest request = PointChargeRequest.builder()
			.userId(u.getId())
			.amount(5000L)
			.idempotencyKey(TestFixture.idemKey())
			.build();
		String json = objectMapper.writeValueAsString(request);

		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/points/charge")
				.contentType(MediaType.APPLICATION_JSON)
				.content(json)
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.chargedAmount").value(5000L))
			.andExpect(jsonPath("$.balanceAfterChange").value(5000L))
			.andDo(print());

		// 주문 api 호출 후 잔액 확인(point의 reserved 필드 변한거 검증하면 될듯)
		OrderDraftCreateRequest orderRequest = OrderDraftCreateRequest.builder()
			.cartId(cart.getId())
			.addressId(addr.getId())
			.memo("빠른 배송 부탁")
			.userId(u.getId())
			.couponId(null)
			.pointUseAmount(3000L)
			.idempotencyKey(TestFixture.idemKey())
			.build();

		String orderJson = objectMapper.writeValueAsString(orderRequest);
		// 주문
		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/order/create")
				.contentType(MediaType.APPLICATION_JSON)
				.content(orderJson)
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.itemTotal").value(4000L))
			.andExpect(jsonPath("$.payAmount").value(1000L))
			.andDo(print());

		Order order = orderRepository.findByUserIdAndIdempotencyKey(u.getId(), orderRequest.getIdempotencyKey()).get();

		PayRequest payRequest = PayRequest.of(TestFixture.idemKey(), order.getId());
		String payCreateReq = objectMapper.writeValueAsString(payRequest);

		// 결제
		mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/payment/create")
				.contentType(MediaType.APPLICATION_JSON)
				.content(payCreateReq)
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount").value(1000L))
			.andExpect(jsonPath("$.orderId").value(order.getId()))
			.andExpect(jsonPath("$.status").value(PaymentStatus.SUCCESS.toString()))
			.andDo(print());


		OutboxEvent outboxEvent = outboxEventRepository.findByAggregateIdAndAggregateTypeAndEventType(
			order.getId(),
			AggregateType.ORDER,
			EventType.PAYMENT_COMPLETION_GIVE_POINT
		).get();

		ReflectionTestUtils.setField(outboxEvent, "payload", "{not-json}");
		outboxEventRepository.saveAndFlush(outboxEvent);

		Assertions.assertNotNull(outboxEvent);
		Assertions.assertEquals(order.getId(), outboxEvent.getAggregateId());
		Assertions.assertEquals(OutboxStatus.PENDING.toString(), outboxEvent.getStatus().toString());

		outboxProcessor.processOutbox();

		OutboxEvent outboxEventAfterProcessor = outboxEventRepository.findByAggregateIdAndAggregateTypeAndEventType(
			order.getId(),
			AggregateType.ORDER,
			EventType.PAYMENT_COMPLETION_GIVE_POINT
		).get();

		Assertions.assertEquals(OutboxStatus.FAILED.toString(), outboxEventAfterProcessor.getStatus().toString());
		Assertions.assertEquals(1L, outboxEventAfterProcessor.getRetryCount());
	}
}
