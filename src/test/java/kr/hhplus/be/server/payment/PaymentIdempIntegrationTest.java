package kr.hhplus.be.server.payment;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.FixturePersist;
import kr.hhplus.be.server.TestFixture;
import kr.hhplus.be.server.api.order.request.OrderDraftCreateRequest;
import kr.hhplus.be.server.api.payment.request.PayRequest;
import kr.hhplus.be.server.api.payment.request.PaymentGatewayRequest;
import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;
import kr.hhplus.be.server.api.point.request.PointChargeRequest;
import kr.hhplus.be.server.application.payment.PaymentFacade;
import kr.hhplus.be.server.domain.address.Address;
import kr.hhplus.be.server.domain.cart.Cart;
import kr.hhplus.be.server.domain.cartItem.CartItem;
import kr.hhplus.be.server.domain.inventory.Inventory;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.domain.outbox.AggregateType;
import kr.hhplus.be.server.domain.payment.PaymentGatewayPort;
import kr.hhplus.be.server.domain.payment.PaymentGatewayStatus;
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
import kr.hhplus.be.server.infrastructure.persistence.payment.PaymentRepository;
import kr.hhplus.be.server.infrastructure.persistence.point.PointRepository;
import kr.hhplus.be.server.infrastructure.persistence.product.ProductRepository;
import kr.hhplus.be.server.infrastructure.persistence.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
public class PaymentIdempIntegrationTest {
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
	@MockitoBean("fakePaymentGateWayAdapter")
	private PaymentGatewayPort pgPort;
	@Autowired
	private ApplicationContext ac;
	@Autowired
	private PaymentFacade paymentFacade;
	@Autowired
	private PaymentRepository paymentRepository;
	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Test
	void debug() {
		PaymentGatewayPort bean = ac.getBean(PaymentGatewayPort.class);
		System.out.println("isMock=" + Mockito.mockingDetails(bean).isMock());
	}
	@Test
	void debugIdentity() {
		Object injected = ReflectionTestUtils.getField(paymentFacade, "pgPort");
		System.out.println("same? " + (injected == pgPort));
		System.out.println("injected isMock=" + Mockito.mockingDetails(injected).isMock());
		System.out.println("field isMock=" + Mockito.mockingDetails(pgPort).isMock());
	}

	@Test
	public void givenPointAndOrderReady_whenPaymentOccurTwice_thenIdempotencyOperate() throws Exception {
		// payment가 1개인걸 검증
		// pg가 1번만 호출되는 걸 검증
		// 포인트, 재고 변경 검증
		// 첫번째 요청과 두번째 요청이 같음을 검증
		User u = persist.saveAndFlush(userRepository, TestFixture.user());
		Cart cart = persist.saveAndFlush(cartRepository, TestFixture.cart(u));
		Product p = persist.save(productRepository, TestFixture.product("콜라", 2000L));
		CartItem cartItem1 = persist.save(cartItemRepository, TestFixture.cartItem(cart, p, 2));
		Inventory inv = persist.save(inventoryRepository, TestFixture.inventory(p, 10L));
		Address addr = persist.saveAndFlush(addressRepository, TestFixture.address(u));
		Long pointChargeAmount = 5000L;
		PaymentGatewayResponse pgPointChargeRes = PaymentGatewayResponse.of(TestFixture.pgTxId(),
			PaymentGatewayStatus.SUCCESS, pointChargeAmount);
		when(pgPort.requestPayment(any())).thenReturn(pgPointChargeRes);
		// 포인트 충전 api 호출 후 point 잔액 확인
		PointChargeRequest request = PointChargeRequest.builder()
			.userId(u.getId())
			.amount(pointChargeAmount)
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

		// 밑에 결제 멱등성 검증을 위해 삭제
		clearInvocations(pgPort);

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
		String payIdemKey = TestFixture.idemKey();
		PayRequest payRequest = PayRequest.of(payIdemKey, order.getId());
		String payCreateReq = objectMapper.writeValueAsString(payRequest);

		PaymentGatewayResponse pgRes = PaymentGatewayResponse.of(TestFixture.pgTxId(),
			PaymentGatewayStatus.SUCCESS, 1000L);
		Mockito.when(pgPort.requestPayment(any())).thenReturn(pgRes);
		//when(pgPort.requestPayment(any(PaymentGatewayRequest.class))).thenReturn(pgRes);

		// 결제1
		MvcResult result1 = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/payment/create")
				.contentType(MediaType.APPLICATION_JSON)
				.content(payCreateReq)
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount").value(1000L))
			.andExpect(jsonPath("$.orderId").value(order.getId()))
			.andExpect(jsonPath("$.status").value(PaymentStatus.SUCCESS.toString()))
			.andDo(print())
			.andReturn();
		// 결제2
		MvcResult result2 = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/payment/create")
				.contentType(MediaType.APPLICATION_JSON)
				.content(payCreateReq)
			)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.amount").value(1000L))
			.andExpect(jsonPath("$.orderId").value(order.getId()))
			.andExpect(jsonPath("$.status").value(PaymentStatus.SUCCESS.toString()))
			.andDo(print())
			.andReturn();
		JsonNode r1 = objectMapper.readTree(result1.getResponse().getContentAsString());
		JsonNode r2 = objectMapper.readTree(result2.getResponse().getContentAsString());
		Assertions.assertEquals(r1, r2);

		Point pointAfterPayment = pointRepository.findByUserId(u.getId()).get();
		Inventory inventoryAfterPayment = inventoryRepository.findByProductId(p.getId()).get();
		Order orderAfterPayment = orderRepository.findByUserIdAndIdempotencyKey(u.getId(), orderRequest.getIdempotencyKey()).get();
		Long count = paymentRepository.countByOrderIdAndIdempotencyKey(order.getId(), payIdemKey);
		Long outboxCount = outboxEventRepository.countByAggregateIdAndAggregateType(order.getId(), AggregateType.ORDER);

		Assertions.assertEquals(2000L, pointAfterPayment.getBalance());
		Assertions.assertEquals(8L, inventoryAfterPayment.getStock());
		Assertions.assertEquals(0L, inventoryAfterPayment.getReserved());
		Assertions.assertEquals(OrderStatus.PAID.toString(), orderAfterPayment.getStatus().toString());
		Assertions.assertEquals(1, count);
		Assertions.assertEquals(2, outboxCount);

		// 포인트 충전 1회 + 결제 1회 = 2회
		verify(pgPort, times(1)).requestPayment(any());

	}
}
