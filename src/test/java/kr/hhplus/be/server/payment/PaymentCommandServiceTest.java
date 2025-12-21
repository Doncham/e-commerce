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

import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.api.payment.request.PayResponse;
import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;
import kr.hhplus.be.server.application.order.OrderPort;
import kr.hhplus.be.server.application.payment.PaymentCommandService;
import kr.hhplus.be.server.application.payment.dto.PaymentAttempt;
import kr.hhplus.be.server.domain.inventory.Inventory;
import kr.hhplus.be.server.infrastructure.persistence.inventory.InventoryRepository;
import kr.hhplus.be.server.domain.inventoryReserve.InventoryReservation;
import kr.hhplus.be.server.domain.inventoryReserve.InventoryReserveStatus;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.domain.order.ShippingInfo;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentGatewayStatus;
import kr.hhplus.be.server.domain.payment.PaymentPort;
import kr.hhplus.be.server.domain.payment.PaymentStatus;
import kr.hhplus.be.server.infrastructure.persistence.inventoryReserve.InventoryReserveRepository;
import kr.hhplus.be.server.infrastructure.persistence.outbox.OutboxEventRepository;

@ExtendWith(MockitoExtension.class)
class PaymentCommandServiceTest {

	@InjectMocks
	private PaymentCommandService paymentCommandService;

	@Mock
	private PaymentPort paymentPort;
	@Mock
	private OrderPort orderPort;
	@Mock
	private OutboxEventRepository outboxEventRepository;
	@Mock
	private ObjectMapper objectMapper;

	@Mock
	private InventoryReserveRepository invReserveRepo;
	@Mock
	private InventoryRepository invRepo;

	@Mock
	private ShippingInfo shippingInfo;

	private Order makeCreatedOrder(Long orderId, String idemKey, long payAmount) {
		var user = mock(kr.hhplus.be.server.domain.user.User.class);

		OrderProduct p1 = OrderProduct.create(1L, "A", 1000L, 2); // 2000
		OrderProduct p2 = OrderProduct.create(2L, "B", 500L, 1);  // 500
		Order order = Order.createDraft(user, shippingInfo, idemKey);
		order.completeOrderDraft(List.of(p1, p2), 1L, 0L, "memo", 0L);

		ReflectionTestUtils.setField(order, "id", orderId);
		return order;
	}

	@Test
	void preparePayment_whenOrderNotPaid_thenReturnAttempt() {
		Long orderId = 1L;
		String idemKey = "idem-123";
		Order order = makeCreatedOrder(orderId, idemKey, 2500L);
		when(orderPort.loadOrderForUpdate(orderId)).thenReturn(order);

		// 저장된 Payment를 미리 만들어서 id를 심어둠
		Payment saved = Payment.createPayment(order, idemKey, 2500L);
		ReflectionTestUtils.setField(saved, "id", 10L);

		// saveAndFlush가 saved를 반환하도록
		when(paymentPort.saveAndFlush(any(Payment.class))).thenReturn(saved);

		PaymentAttempt attempt = paymentCommandService.preparePayment(orderId, idemKey);

		assertEquals(10L, attempt.getPaymentId());
		assertEquals(2500L, attempt.getAmount());
		assertEquals(idemKey, attempt.getIdempotencyKey());

	}

	@Test
	void completePayment_success_whenAmountMatches_thenOrderPaid_confirmReservations_andPublishOutbox() throws
		Exception {
		// given
		Long orderId = 1L;
		String idemKey = "idem-123";
		Order order = makeCreatedOrder(orderId, idemKey, 2500L);

		Payment payment = Payment.createPayment(order, idemKey, 2500L);
		ReflectionTestUtils.setField(payment, "id", 10L);

		when(paymentPort.loadForUpdate(10L)).thenReturn(payment);
		when(orderPort.loadOrderForUpdate(orderId)).thenReturn(order);

		// 예약 1개
		InventoryReservation r1 = InventoryReservation.reserve(orderId, 100L, 2L);
		when(invReserveRepo.findByOrderIdAndStatus(orderId, InventoryReserveStatus.RESERVED))
			.thenReturn(List.of(r1));

		Inventory inv = mock(Inventory.class);
		when(inv.getId()).thenReturn(100L);
		when(invRepo.findByIdsForUpdate(List.of(100L))).thenReturn(List.of(inv));

		when(objectMapper.writeValueAsString(any())).thenReturn("{}");

		PaymentGatewayResponse pgResp =
			PaymentGatewayResponse.of("tx-1", PaymentGatewayStatus.SUCCESS, 2500L);

		// when
		PayResponse res = paymentCommandService.completePayment(10L, pgResp);

		// then: 상태 변화
		assertEquals(OrderStatus.PAID, order.getStatus());
		assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
		assertEquals("tx-1", payment.getPgTransactionId());

		// then: 예약 확정 호출
		verify(inv).confirmReserve(2L);
		assertEquals(InventoryReserveStatus.CONFIRMED, r1.getStatus());

		// then: outbox 발행
		verify(outboxEventRepository).save(any());
		assertEquals(orderId, res.getOrderId());
		assertEquals(PaymentStatus.SUCCESS, res.getStatus());
	}

	@Test
	void completePayment_failure_thenOrderFailed_releaseReservations_andNoOutbox() {
		// given
		Long orderId = 1L;
		String idemKey = "idem-123";
		Order order = makeCreatedOrder(orderId, idemKey, 2500L);

		Payment payment = Payment.createPayment(order, idemKey, 2500L);
		ReflectionTestUtils.setField(payment, "id", 10L);

		when(paymentPort.loadForUpdate(10L)).thenReturn(payment);
		when(orderPort.loadOrderForUpdate(orderId)).thenReturn(order);

		InventoryReservation r1 = InventoryReservation.reserve(orderId, 100L, 2L);
		when(invReserveRepo.findByOrderIdAndStatus(orderId, InventoryReserveStatus.RESERVED))
			.thenReturn(List.of(r1));

		Inventory inv = mock(Inventory.class);
		when(inv.getId()).thenReturn(100L);
		when(invRepo.findByIdsForUpdate(List.of(100L))).thenReturn(List.of(inv));

		PaymentGatewayResponse pgResp =
			PaymentGatewayResponse.of("tx-1", PaymentGatewayStatus.FAILURE, 2500L);

		// when
		PayResponse res = paymentCommandService.completePayment(10L, pgResp);

		// then
		assertEquals(OrderStatus.FAILED, order.getStatus());
		assertEquals(PaymentStatus.FAILURE, payment.getStatus());

		verify(inv).releaseReserve(2L);
		assertEquals(InventoryReserveStatus.RELEASED, r1.getStatus());

		verify(outboxEventRepository, never()).save(any());
		assertEquals(PaymentStatus.FAILURE, res.getStatus());
	}
}