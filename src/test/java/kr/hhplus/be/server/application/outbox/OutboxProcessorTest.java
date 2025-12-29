package kr.hhplus.be.server.application.outbox;

import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.application.point.PointCommandService;
import kr.hhplus.be.server.domain.outbox.AggregateType;
import kr.hhplus.be.server.domain.outbox.EventType;
import kr.hhplus.be.server.domain.outbox.OutboxEvent;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import kr.hhplus.be.server.domain.outbox.PaymentCompletedPayload;
import kr.hhplus.be.server.infrastructure.persistence.outbox.OutboxEventRepository;

@ExtendWith(MockitoExtension.class)
class OutboxProcessorTest {
	@InjectMocks
	private OutboxProcessor outboxProcessor;
	@Mock
	private PointCommandService pointService;
	@Mock
	private OutboxEventRepository outboxEventRepository;
	@Mock
	private ObjectMapper objectMapper;
	@Mock
	private SingleOutboxProcessService processOutboxService;


	@Test
	void givenOutboxEventList_whenProcessOutbox_thenEventStatusBecomeProcessed() throws JsonProcessingException {
		// given
		Long userId = 1L;
		OutboxEvent event1 = OutboxEvent.of(
			AggregateType.ORDER, 1L, EventType.PAYMENT_COMPLETION_GIVE_POINT, "payload");
		OutboxEvent event2 = OutboxEvent.of(
			AggregateType.ORDER, 2L, EventType.PAYMENT_COMPLETION_GIVE_POINT, "payload");
		ReflectionTestUtils.setField(event1, "id", 1L);
		ReflectionTestUtils.setField(event2, "id", 2L);
		List<OutboxEvent> outboxEvents = List.of(event1, event2);

		when(outboxEventRepository.findByStatusOrderByIdAsc(
			any(OutboxStatus.class),
			any(LocalDateTime.class),
			any(PageRequest.class)))
			.thenReturn(outboxEvents);

		PaymentCompletedPayload payload1 = PaymentCompletedPayload.of(userId, 1L, 2500L, "tx1");
		PaymentCompletedPayload payload2 = PaymentCompletedPayload.of(userId, 2L, 2500L, "tx2");


		// when
		outboxProcessor.processOutbox();

		// then
		verify(processOutboxService, times(2)).processOne(anyLong());
	}

}