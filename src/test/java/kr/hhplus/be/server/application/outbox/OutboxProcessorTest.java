package kr.hhplus.be.server.application.outbox;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.application.point.PointService;
import kr.hhplus.be.server.domain.outbox.OutboxEvent;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import kr.hhplus.be.server.domain.outbox.PaymentCompletedPayload;
import kr.hhplus.be.server.infrastructure.persistence.outbox.OutboxEventRepository;

@ExtendWith(MockitoExtension.class)
class OutboxProcessorTest {
	@InjectMocks
	private OutboxProcessor outboxProcessor;
	@Mock
	private PointService pointService;
	@Mock
	private OutboxEventRepository outboxEventRepository;
	@Mock
	private ObjectMapper objectMapper;


	@Test
	void givenOutboxEventList_whenProcessOutbox_thenEventStatusBecomeProcessed() throws JsonProcessingException {
		Long userId = 1L;

		OutboxEvent event1 = OutboxEvent.of(
			"ORDER", 1L, "PAYMENT_COMPLETED", "payload");
		OutboxEvent event2 = OutboxEvent.of(
			"ORDER", 2L, "PAYMENT_COMPLETED", "payload");

		// given
		List<OutboxEvent> outboxEvents = List.of(event1, event2);

		when(outboxEventRepository.findTop100ByStatusOrderByIdAsc(OutboxStatus.PENDING))
			.thenReturn(outboxEvents);

		PaymentCompletedPayload payload1 = PaymentCompletedPayload.of(userId, 1L, 2500L, "tx1");
		PaymentCompletedPayload payload2 = PaymentCompletedPayload.of(userId, 2L, 2500L, "tx2");

		when(objectMapper.readValue(anyString(), eq(PaymentCompletedPayload.class))).thenReturn(payload1)
			.thenReturn(payload2);

		// when
		outboxProcessor.processOutbox();

		// then
		Assertions.assertEquals(OutboxStatus.PROCESSED, event1.getStats());
		Assertions.assertEquals(OutboxStatus.PROCESSED, event2.getStats());
		verify(pointService, times(2))
			.earnForOrder(anyLong(), anyLong(), anyLong());

	}

	@Test
	void givenPointServiceFailure_whenProcessOutbox_thenEventStatusBecomeFailed() throws Exception {
		// given
		OutboxEvent event = OutboxEvent.of("ORDER", 1L, "PAYMENT_COMPLETED", "payload");
		when(outboxEventRepository.findTop100ByStatusOrderByIdAsc(OutboxStatus.PENDING))
			.thenReturn(List.of(event));

		PaymentCompletedPayload payload = PaymentCompletedPayload.of(1L, 1L, 2500L, "tx1");
		when(objectMapper.readValue(anyString(), eq(PaymentCompletedPayload.class)))
			.thenReturn(payload);

		doThrow(new RuntimeException("db error"))
			.when(pointService).earnForOrder(1L, 1L, 2500L);

		// when
		outboxProcessor.processOutbox();

		// then
		assertEquals(OutboxStatus.FAILED, event.getStats());
	}



}