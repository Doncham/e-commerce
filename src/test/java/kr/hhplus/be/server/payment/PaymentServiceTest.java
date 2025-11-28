package kr.hhplus.be.server.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import kr.hhplus.be.server.order.Order;
import kr.hhplus.be.server.order.OrderRepository;
import kr.hhplus.be.server.order.OrderStatus;
import kr.hhplus.be.server.order.ShippingInfo;
import kr.hhplus.be.server.orderproduct.OrderProduct;
import kr.hhplus.be.server.payment.exception.PaidOrderHavePaymentException;
import kr.hhplus.be.server.payment.exception.PayAmountMisMatchException;
import kr.hhplus.be.server.payment.request.PayResponse;
import kr.hhplus.be.server.payment.request.PaymentGatewayRequest;
import kr.hhplus.be.server.payment.response.PaymentGatewayResponse;
import kr.hhplus.be.server.user.User;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
	@InjectMocks
	private PaymentService paymentService;
	@Mock
	private PaymentGatewayClient pgClient;
	@Mock
	private PaymentRepository paymentRepository;
	@Mock
	private OrderRepository orderRepository;
	@Mock
	User user;
	@Mock
	ShippingInfo shippingInfo;
	// 결제 성공
	@Test
	void givenValidOrderId_whenPay_thenChangeOrderStatusAndSavePayment() {
		// given
		Long orderId = 1L;
		OrderProduct orderProduct1 = OrderProduct.create(1L, "Product A", 1000L, 2);
		OrderProduct orderProduct2 = OrderProduct.create(2L, "Product B", 500L, 1);
		// order 만들기
		Order order = Order.createOrder(user, shippingInfo, List.of(orderProduct1, orderProduct2), 1L, 0L, "memo");
		ReflectionTestUtils.setField(order, "id", orderId);
		when(orderRepository.findByIdForUpdate(orderId)).thenReturn(
			Optional.of(order)
		);


		PaymentGatewayResponse pgResponse = PaymentGatewayResponse.of("tx123", PaymentGatewayStatus.SUCCESS, 2500L);
		when(pgClient.requestPayment(any(PaymentGatewayRequest.class)))
			.thenReturn(pgResponse);

		// when
		PayResponse payResponse = paymentService.pay(orderId);

		// then
		// order 상태 확인
		ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);

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
		OrderProduct orderProduct1 = OrderProduct.create(1L, "Product A", 1000L, 2);
		OrderProduct orderProduct2 = OrderProduct.create(2L, "Product B", 500L, 1);
		Order order = Order.createOrder(user, shippingInfo, List.of(orderProduct1, orderProduct2), 1L, 0L, "memo");
		ReflectionTestUtils.setField(order, "id", orderId);

		when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

		PaymentGatewayResponse pgResponse =
			PaymentGatewayResponse.of("tx123", PaymentGatewayStatus.FAILURE, 2500L);
		when(pgClient.requestPayment(any())).thenReturn(pgResponse);


		// when
		PayResponse response = paymentService.pay(orderId);

		// then
		assertEquals(OrderStatus.FAILED, order.getStatus());
		assertNull(response.getTransactionId());
		assertEquals("FAILED", response.getStatus());
		assertEquals(2500L, response.getAmount());
		assertEquals(orderId, response.getOrderId());

	}

	@Test
	// 이미 결제된 주문에 대해 다시 결제 시도 시도 시 멱
	void givenAlreadyPaidOrder_whenPay_thenSkipPaymentAndPgNotCalled() {
		// given
		Long orderId = 1L;
		Order order = mock(Order.class);

		when(orderRepository.findByIdForUpdate(orderId))
			.thenReturn(Optional.of(order));
		when(order.isPaid()).thenReturn(true);

		// when
		assertThrows(PaidOrderHavePaymentException.class, () -> paymentService.pay(orderId));

		// then
		verify(pgClient, never()).requestPayment(any());
		verify(paymentRepository, never()).saveAndFlush(any());
	}

	@Test
	// PG에서 결제 금액이 다르게 오는 경우
	void givenAmountMismatchFromPg_whenPay_thenThrowException() {
		// given
		Long orderId = 1L;
		OrderProduct orderProduct1 = OrderProduct.create(1L, "Product A", 1000L, 2);
		OrderProduct orderProduct2 = OrderProduct.create(2L, "Product B", 500L, 1);
		Order order = Order.createOrder(user, shippingInfo, List.of(orderProduct1, orderProduct2), 1L, 0L, "memo");
		ReflectionTestUtils.setField(order, "id", orderId);

		when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

		PaymentGatewayResponse pgResponse =
			PaymentGatewayResponse.of("tx123", PaymentGatewayStatus.SUCCESS, 2000L); // 금액 불일치
		when(pgClient.requestPayment(any())).thenReturn(pgResponse);

		// when & then
		assertThrows(PayAmountMisMatchException.class, () -> paymentService.pay(orderId));
	}


}