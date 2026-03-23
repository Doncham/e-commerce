package kr.hhplus.be.server.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.application.payment.PaymentOutboxPublisher;
import kr.hhplus.be.server.application.product.PopularProductIncrementPayload;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.ShippingInfo;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.domain.outbox.AggregateType;
import kr.hhplus.be.server.domain.outbox.EventType;
import kr.hhplus.be.server.domain.outbox.OutboxEvent;
import kr.hhplus.be.server.domain.outbox.PaymentCompletedPayload;
import kr.hhplus.be.server.domain.user.User;
import kr.hhplus.be.server.infrastructure.persistence.outbox.OutboxEventRepository;

@ExtendWith(MockitoExtension.class)
class PaymentOutboxPublisherTest {

	@InjectMocks
	private PaymentOutboxPublisher paymentOutboxPublisher;

	@Mock
	private OutboxEventRepository outboxEventRepository;

	@Mock
	private ObjectMapper objectMapper;

	@Mock
	private ShippingInfo shippingInfo;

	private Order makeCreatedOrder(Long orderId, String idemKey) {
		User user = mock(User.class);

		OrderProduct p1 = OrderProduct.create(1L, "신발", 1000L, null); // 2000
		OrderProduct p2 = OrderProduct.create(2L, "모자", 500L, null);  // 500

		Order order = Order.createDraft(user, shippingInfo, idemKey);
		order.completeOrderDraft(List.of(p1, p2), 0L, "memo", 0L);

		ReflectionTestUtils.setField(order, "id", orderId);
		return order;
	}

	@Test
	void publishPointEarnOutbox_thenSavePointEarnEvent() throws Exception {
		// given
		Long orderId = 1L;
		Order order = makeCreatedOrder(orderId, "idem-123");

		when(objectMapper.writeValueAsString(any())).thenReturn("{\"type\":\"point\"}");

		// when
		paymentOutboxPublisher.publishPointEarn(order, "tx-1");

		// then
		verify(objectMapper).writeValueAsString(isA(PaymentCompletedPayload.class));

		ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
		verify(outboxEventRepository).save(captor.capture());

		OutboxEvent saved = captor.getValue();
		assertOutboxEvent(
			saved,
			AggregateType.ORDER,
			orderId,
			EventType.PAYMENT_COMPLETION_GIVE_POINT,
			"{\"type\":\"point\"}"
		);
	}

	@Test
	void publishPopularIncrementOutbox_thenSavePopularIncrementEvent() throws Exception {
		// given
		Long orderId = 1L;
		Order order = makeCreatedOrder(orderId, "idem-123");
		LocalDateTime paidAt = LocalDateTime.of(2026, 3, 20, 10, 15);

		when(objectMapper.writeValueAsString(any())).thenReturn("{\"type\":\"popular\"}");

		// when
		paymentOutboxPublisher.publishPopularIncrement(order, paidAt);

		// then
		verify(objectMapper).writeValueAsString(isA(PopularProductIncrementPayload.class));

		ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
		verify(outboxEventRepository).save(captor.capture());

		OutboxEvent saved = captor.getValue();
		assertOutboxEvent(
			saved,
			AggregateType.ORDER,
			orderId,
			EventType.ORDER_PAID_POPULAR_INCREMENT,
			"{\"type\":\"popular\"}"
		);
	}

	@Test
	void publishPaymentSuccess_thenSaveTwoOutboxEvents() throws Exception {
		// given
		Long orderId = 1L;
		Order order = makeCreatedOrder(orderId, "idem-123");
		LocalDateTime paidAt = LocalDateTime.of(2026, 3, 20, 10, 15);

		when(objectMapper.writeValueAsString(any()))
			.thenReturn("{\"type\":\"point\"}", "{\"type\":\"popular\"}");

		// when
		paymentOutboxPublisher.publishPaymentSuccess(order, "tx-1", paidAt);

		// then
		ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
		verify(objectMapper, times(2)).writeValueAsString(payloadCaptor.capture());

		List<Object> payloads = payloadCaptor.getAllValues();
		assertEquals(2, payloads.size());
		assertTrue(payloads.get(0) instanceof PaymentCompletedPayload);
		assertTrue(payloads.get(1) instanceof PopularProductIncrementPayload);

		ArgumentCaptor<OutboxEvent> outboxCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
		verify(outboxEventRepository, times(2)).save(outboxCaptor.capture());

		List<OutboxEvent> savedEvents = outboxCaptor.getAllValues();
		assertEquals(2, savedEvents.size());

		assertOutboxEvent(
			savedEvents.get(0),
			AggregateType.ORDER,
			orderId,
			EventType.PAYMENT_COMPLETION_GIVE_POINT,
			"{\"type\":\"point\"}"
		);

		assertOutboxEvent(
			savedEvents.get(1),
			AggregateType.ORDER,
			orderId,
			EventType.ORDER_PAID_POPULAR_INCREMENT,
			"{\"type\":\"popular\"}"
		);
	}

	@Test
	void publishPointEarnOutbox_whenSerializationFails_thenThrowRuntimeException() throws Exception {
		// given
		Order order = makeCreatedOrder(1L, "idem-123");

		when(objectMapper.writeValueAsString(any()))
			.thenThrow(new JsonProcessingException("serialize fail") {});

		// when & then
		assertThrows(RuntimeException.class,
			() -> paymentOutboxPublisher.publishPointEarn(order, "tx-1"));

		verify(outboxEventRepository, never()).save(any());
	}

	private void assertOutboxEvent(
		OutboxEvent event,
		AggregateType expectedAggregateType,
		Long expectedAggregateId,
		EventType expectedEventType,
		String expectedPayload
	) {
		assertEquals(expectedAggregateType, ReflectionTestUtils.getField(event, "aggregateType"));
		assertEquals(expectedAggregateId, ReflectionTestUtils.getField(event, "aggregateId"));
		assertEquals(expectedEventType, ReflectionTestUtils.getField(event, "eventType"));
		assertEquals(expectedPayload, ReflectionTestUtils.getField(event, "payload"));
	}
}