package kr.hhplus.be.server.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import kr.hhplus.be.server.api.payment.request.PayRequest;
import kr.hhplus.be.server.application.payment.PaymentService;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.domain.order.ShippingInfo;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentGatewayStatus;
import kr.hhplus.be.server.domain.payment.PaymentStatus;
import kr.hhplus.be.server.domain.payment.exception.PayAmountMisMatchException;
import kr.hhplus.be.server.application.order.OrderPort;
import kr.hhplus.be.server.domain.payment.PaymentGatewayPort;
import kr.hhplus.be.server.domain.payment.PaymentPort;
import kr.hhplus.be.server.api.payment.request.PayResponse;
import kr.hhplus.be.server.api.payment.request.PaymentGatewayRequest;
import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;
import kr.hhplus.be.server.domain.user.User;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
	@InjectMocks
	private PaymentService paymentService;
	@Mock
	private PaymentGatewayPort pgClient;
	@Mock
	private PaymentPort paymentPort;
	@Mock
	private OrderPort orderPort;
	@Mock
	User user;
	@Mock
	ShippingInfo shippingInfo;
	// 결제 성공
	@Test
	void givenValidOrderId_whenPay_thenChangeOrderStatusAndSavePayment() {
		// given
		Long orderId = 1L;
		String idempotencyKey = "unique-key-123";
		PayRequest request = PayRequest.builder()
			.idempotencyKey(idempotencyKey)
			.orderId(orderId)
			.build();

		OrderProduct orderProduct1 = OrderProduct.create(1L, "Product A", 1000L, 2);
		OrderProduct orderProduct2 = OrderProduct.create(2L, "Product B", 500L, 1);
		// order 만들기
		Order order = Order.createOrder(user, shippingInfo, List.of(orderProduct1, orderProduct2), 1L, 0L, "memo");
		ReflectionTestUtils.setField(order, "id", orderId);
		when(orderPort.loadOrderForUpdate(orderId)).thenReturn(
			order
		);



		PaymentGatewayResponse pgResponse = PaymentGatewayResponse.of("tx123", PaymentGatewayStatus.SUCCESS, 2500L);
		when(pgClient.requestPayment(any(PaymentGatewayRequest.class)))
			.thenReturn(pgResponse);

		// when
		PayResponse payResponse = paymentService.pay(request);

		// then
		// order 상태 확인
		assertEquals(OrderStatus.PAID, order.getStatus());
		assertEquals("tx123", payResponse.getTransactionId());
		assertEquals("PAID", payResponse.getStatus());
		assertEquals(2500L, payResponse.getAmount());
		assertEquals(orderId, payResponse.getOrderId());

	}

	// 멱등성 테스트: PG 실패 시 응답 체크
	@Test
	void givenGatewayFailure_whenPay_thenOrderFailedAndPaymentSavedAsFailed() {
		// given
		Long orderId = 1L;
		String idempotencyKey = "unique-key-123";
		PayRequest request = PayRequest.builder()
			.idempotencyKey(idempotencyKey)
			.orderId(orderId)
			.build();

		OrderProduct orderProduct1 = OrderProduct.create(1L, "Product A", 1000L, 2);
		OrderProduct orderProduct2 = OrderProduct.create(2L, "Product B", 500L, 1);
		Order order = Order.createOrder(user, shippingInfo, List.of(orderProduct1, orderProduct2), 1L, 0L, "memo");
		ReflectionTestUtils.setField(order, "id", orderId);

		when(orderPort.loadOrderForUpdate(orderId)).thenReturn(order);

		PaymentGatewayResponse pgResponse =
			PaymentGatewayResponse.of("tx123", PaymentGatewayStatus.FAILURE, 2500L);
		when(pgClient.requestPayment(any())).thenReturn(pgResponse);


		// when
		PayResponse response = paymentService.pay(request);

		// then
		assertEquals(OrderStatus.FAILED, order.getStatus());
		assertNull(response.getTransactionId());
		assertEquals("FAILED", response.getStatus());
		assertEquals(2500L, response.getAmount());
		assertEquals(orderId, response.getOrderId());

	}

	@Test
	// 이미 결제된 주문에 대해 다시 결제 시도 시도 시 이전에 성공한 결제 성공 정보를 제공한다..
	void givenAlreadyPaidOrder_whenPay_thenSkipPaymentAndPgNotCalled() {
		// given
		Long orderId = 1L;
		String idempotencyKey = "unique-key-123";
		PayRequest request = PayRequest.builder()
			.idempotencyKey(idempotencyKey)
			.orderId(orderId)
			.build();
		Order order = mock(Order.class);
		Payment payment = mock(Payment.class);

		when(orderPort.loadOrderForUpdate(orderId))
			.thenReturn(order);
		when(order.isPaid()).thenReturn(true);
		when(order.getId()).thenReturn(orderId);
		when(order.getStatus()).thenReturn(OrderStatus.PAID);
		when(order.getPayAmount()).thenReturn(2500L);

		when(paymentPort.loadLastSuccessPaymentByOrderId(orderId, PaymentStatus.SUCCESS))
			.thenReturn(payment);
		when(payment.getPgTransactionId())
			.thenReturn("txAlready123");

		// when
		//assertThrows(PaidOrderHavePaymentException.class, () -> paymentService.pay(orderId));
		PayResponse response = paymentService.pay(request);

		// then
		assertEquals(orderId, response.getOrderId());
		assertEquals("PAID", response.getStatus());
		assertEquals("txAlready123", response.getTransactionId());
		assertEquals(2500L, response.getAmount());
		verify(pgClient, never()).requestPayment(any());

	}

	@Test
	// PG에서 결제 금액이 다르게 오는 경우
	void givenAmountMismatchFromPg_whenPay_thenThrowException() {
		// given
		Long orderId = 1L;
		String idempotencyKey = "unique-key-123";
		PayRequest request = PayRequest.builder()
			.idempotencyKey(idempotencyKey)
			.orderId(orderId)
			.build();

		OrderProduct orderProduct1 = OrderProduct.create(1L, "Product A", 1000L, 2);
		OrderProduct orderProduct2 = OrderProduct.create(2L, "Product B", 500L, 1);
		Order order = Order.createOrder(user, shippingInfo, List.of(orderProduct1, orderProduct2), 1L, 0L, "memo");
		ReflectionTestUtils.setField(order, "id", orderId);

		when(orderPort.loadOrderForUpdate(orderId)).thenReturn(order);

		PaymentGatewayResponse pgResponse =
			PaymentGatewayResponse.of("tx123", PaymentGatewayStatus.SUCCESS, 2000L); // 금액 불일치
		when(pgClient.requestPayment(any())).thenReturn(pgResponse);

		// when & then
		assertThrows(PayAmountMisMatchException.class, () -> paymentService.pay(request));
	}


}