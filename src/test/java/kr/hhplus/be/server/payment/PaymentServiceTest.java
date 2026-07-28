package kr.hhplus.be.server.payment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import kr.hhplus.be.server.api.payment.request.PayResponse;
import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;
import kr.hhplus.be.server.application.payment.PaymentOutboxPublisher;
import kr.hhplus.be.server.application.payment.PaymentReservationProcessor;
import kr.hhplus.be.server.application.payment.PaymentService;
import kr.hhplus.be.server.application.payment.dto.PaymentAttempt;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayType;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.domain.order.ShippingInfo;
import kr.hhplus.be.server.domain.order.exception.OrderAlreadyPaidOrderException;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentGatewayStatus;
import kr.hhplus.be.server.domain.payment.PaymentStatus;
import kr.hhplus.be.server.domain.user.User;
import kr.hhplus.be.server.infrastructure.persistence.order.OrderRepository;
import kr.hhplus.be.server.infrastructure.persistence.payment.PaymentRepository;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

	@InjectMocks
	private PaymentService paymentService;

	@Mock
	private PaymentRepository paymentRepository;

	@Mock
	private OrderRepository orderRepository;

	@Mock
	private PaymentReservationProcessor reservationProcessor;

	@Mock
	private PaymentOutboxPublisher outboxPublisher;

	@Mock
	private ShippingInfo shippingInfo;
	@Mock
	private Clock clock;

	private Order makeCreatedOrder(Long orderId, String idemKey) {
		User user = mock(User.class);

		OrderProduct p1 = OrderProduct.create(1L, "신발", 1000L); // 2000
		OrderProduct p2 = OrderProduct.create(2L, "모자", 500L);  // 500

		Order order = Order.createDraft(user, idemKey);
		order.updateOrderDraft(List.of(p1, p2), "memo", shippingInfo);

		ReflectionTestUtils.setField(order, "id", orderId);
		return order;
	}

	@Test
	void preparePayment_whenOrderNotPaid_thenReturnAttempt() {
		// given
		Long orderId = 1L;
		String idemKey = "idem-123";
		Order order = makeCreatedOrder(orderId, idemKey);

		when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

		Payment saved = Payment.createPayment(order, order.getPaymentAmount(), PaymentGatewayType.TOSS);
		ReflectionTestUtils.setField(saved, "id", 10L);

		when(paymentRepository.save(any(Payment.class))).thenReturn(saved);

		// when
		PaymentAttempt attempt = paymentService.preparePayment(orderId);

		// then
		assertEquals(10L, attempt.getPaymentId());
		assertEquals(order.getPaymentAmount(), attempt.getAmount());
		assertEquals("orderId:" + orderId, attempt.getIdempotencyKey());

		verify(orderRepository).findByIdForUpdate(orderId);
		verify(paymentRepository).save(any(Payment.class));
	}

	@Test
	void preparePayment_whenOrderAlreadyPaid_thenThrowException() {
		// given
		Long orderId = 1L;
		String idemKey = "idem-123";
		Order order = makeCreatedOrder(orderId, idemKey);
		order.paid();

		when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

		// when & then
		assertThrows(OrderAlreadyPaidOrderException.class,
			() -> paymentService.preparePayment(orderId));

		verify(orderRepository).findByIdForUpdate(orderId);
		verify(paymentRepository, never()).saveAndFlush(any());
	}

	@Test
	void completePayment_success_whenPgSuccessAndAmountMatches_thenConfirmAndPublishOutbox() {
		// given
		Long orderId = 1L;
		String idemKey = "idem-123";
		Order order = makeCreatedOrder(orderId, idemKey);

		Payment payment = Payment.createPayment(order, order.getPaymentAmount(), PaymentGatewayType.TOSS);
		ReflectionTestUtils.setField(payment, "id", 10L);

		when(paymentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(payment));
		when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));
		when(clock.instant()).thenReturn(Instant.parse("2026-03-25T10:15:30Z"));
		when(clock.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));


		PaymentGatewayResponse pgResp = PaymentGatewayResponse.of(
			"tx-1",
			PaymentGatewayStatus.SUCCESS,
			order.getPaymentAmount(),
			null,
			null
		);

		// when
		PayResponse response = paymentService.completePayment(10L, pgResp);

		// then
		assertEquals(OrderStatus.PAID, order.getStatus());
		assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
		assertEquals("tx-1", payment.getPgOrderId());

		verify(reservationProcessor).confirm(order);
		verify(reservationProcessor, never()).release(any(Order.class), anyString());

		verify(outboxPublisher).publishPaymentSuccess(eq(order), eq("tx-1"), any(LocalDateTime.class));

		assertEquals(orderId, response.getOrderId());
		assertEquals(PaymentStatus.SUCCESS, response.getStatus());
	}

	@Test
	void completePayment_failure_whenPgFailed_thenReleaseAndNoOutbox() {
		// given
		Long orderId = 1L;
		String idemKey = "idem-123";
		Order order = makeCreatedOrder(orderId, idemKey);

		Payment payment = Payment.createPayment(order, order.getPaymentAmount(), PaymentGatewayType.TOSS);
		ReflectionTestUtils.setField(payment, "id", 10L);

		when(paymentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(payment));
		when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

		PaymentGatewayResponse pgResp = PaymentGatewayResponse.of(
			"tx-1",
			PaymentGatewayStatus.FAILURE,
			order.getPaymentAmount(),
			null,
			null
		);

		// when
		PayResponse response = paymentService.completePayment(10L, pgResp);

		// then
		assertEquals(OrderStatus.FAILED, order.getStatus());
		assertEquals(PaymentStatus.FAILURE, payment.getStatus());
//		assertEquals("tx-1", response.getTransactionId());

		verify(reservationProcessor).release(order, "PG_FAILED");
		verify(reservationProcessor, never()).confirm(any(Order.class));

		verify(outboxPublisher, never()).publishPaymentSuccess(any(Order.class), anyString(), any(LocalDateTime.class));

		assertEquals(orderId, response.getOrderId());
		assertEquals(PaymentStatus.FAILURE, response.getStatus());
	}

	@Test
	void completePayment_failure_whenPaidAmountMismatch_thenReleaseAndNoOutbox() {
		// given
		Long orderId = 1L;
		String idemKey = "idem-123";
		Order order = makeCreatedOrder(orderId, idemKey);

		Payment payment = Payment.createPayment(order, order.getPaymentAmount(), PaymentGatewayType.TOSS);
		ReflectionTestUtils.setField(payment, "id", 10L);


		when(paymentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(payment));
		when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

		PaymentGatewayResponse pgResp = PaymentGatewayResponse.of(
			"tx-1",
			PaymentGatewayStatus.SUCCESS,
			9999L,
			null,
			null
		);

		// when
		PayResponse response = paymentService.completePayment(10L, pgResp);

		// then
		assertEquals(OrderStatus.FAILED, order.getStatus());
		assertEquals(PaymentStatus.FAILURE, payment.getStatus());
		//assertEquals("tx-1", response.getTransactionId());

		verify(reservationProcessor).release(order, "PAY_AMOUNT_MISMATCH");
		verify(reservationProcessor, never()).confirm(any(Order.class));

		verify(outboxPublisher, never()).publishPaymentSuccess(any(Order.class), anyString(), any(LocalDateTime.class));

		assertEquals(orderId, response.getOrderId());
		assertEquals(PaymentStatus.FAILURE, response.getStatus());
	}

	@Test
	void completePayment_failure_whenPaidAmountIsNull_thenReleaseAndNoOutbox() {
		// given
		Long orderId = 1L;
		String idemKey = "idem-123";
		Order order = makeCreatedOrder(orderId, idemKey);

		Payment payment = Payment.createPayment(order, order.getPaymentAmount(), PaymentGatewayType.TOSS);
		ReflectionTestUtils.setField(payment, "id", 10L);

		when(paymentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(payment));
		when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

		PaymentGatewayResponse pgResp = PaymentGatewayResponse.of(
			"tx-1",
			PaymentGatewayStatus.SUCCESS,
			null,
			null,
			null
		);

		// when
		PayResponse response = paymentService.completePayment(10L, pgResp);

		// then
		assertEquals(OrderStatus.FAILED, order.getStatus());
		assertEquals(PaymentStatus.FAILURE, payment.getStatus());

		verify(reservationProcessor).release(order, "PAY_AMOUNT_MISMATCH");
		verify(reservationProcessor, never()).confirm(any(Order.class));
		verify(outboxPublisher, never()).publishPaymentSuccess(any(Order.class), anyString(), any(LocalDateTime.class));

		assertEquals(PaymentStatus.FAILURE, response.getStatus());
	}

	@Test
	void completePayment_whenAlreadyFinalized_thenReturnIdempotentResponse() {
		// given
		Long orderId = 1L;
		String idemKey = "idem-123";
		Order order = makeCreatedOrder(orderId, idemKey);

		Payment payment = Payment.createPayment(order, order.getPaymentAmount(), PaymentGatewayType.TOSS);
		ReflectionTestUtils.setField(payment, "id", 10L);

		payment.paymentSuccess("tx-existing", LocalDateTime.now());
		order.paid();

		when(paymentRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(payment));
		when(orderRepository.findByIdForUpdate(orderId)).thenReturn(Optional.of(order));

		PaymentGatewayResponse pgResp = PaymentGatewayResponse.of(
			"tx-new",
			PaymentGatewayStatus.SUCCESS,
			order.getPaymentAmount(),
			null,
			null
		);

		// when
		PayResponse response = paymentService.completePayment(10L, pgResp);

		// then
		assertEquals(OrderStatus.PAID, order.getStatus());
		assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
		assertEquals("tx-existing", payment.getPgOrderId());

		verify(reservationProcessor, never()).confirm(any(Order.class));
		verify(reservationProcessor, never()).release(any(Order.class), anyString());
		verify(outboxPublisher, never()).publishPaymentSuccess(any(Order.class), anyString(), any(LocalDateTime.class));

		assertEquals(orderId, response.getOrderId());
		assertEquals(PaymentStatus.SUCCESS, response.getStatus());
	}
}