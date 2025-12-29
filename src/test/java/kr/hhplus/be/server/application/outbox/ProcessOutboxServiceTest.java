package kr.hhplus.be.server.application.outbox;

import static org.mockito.Mockito.*;

import java.sql.SQLException;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import kr.hhplus.be.server.domain.outbox.AggregateType;
import kr.hhplus.be.server.domain.outbox.EventType;
import kr.hhplus.be.server.domain.outbox.OutboxBusinessTxService;
import kr.hhplus.be.server.domain.outbox.OutboxEvent;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import kr.hhplus.be.server.domain.outbox.OutboxStatusService;
import kr.hhplus.be.server.infrastructure.persistence.outbox.OutboxEventRepository;

@ExtendWith(MockitoExtension.class)
class SingleOutboxProcessServiceTest {

	@Mock OutboxEventRepository outboxEventRepo;
	@Mock OutboxStatusService outboxStatusService;
	@Mock OutboxBusinessTxService outboxBusinessTxService;

	@InjectMocks SingleOutboxProcessService service;

	private Long eventId;

	@BeforeEach
	void setUp() {
		eventId = 1L;
	}

	@Test
	void success_callsBusinessTx_only() throws Exception {
		// when
		service.processOne(eventId);

		// then
		verify(outboxBusinessTxService).handleAndMarkProcessedTx(eventId);
		verifyNoInteractions(outboxStatusService);
		verifyNoInteractions(outboxEventRepo); // 성공 경로에선 retryCount 판단용 조회도 없음
	}

	@Test
	void duplicateUniqueViolation_marksProcessed() throws Exception {
		// given: businessTx에서 DataIntegrityViolationException 던지게
		SQLException sqlEx = new SQLException("Duplicate entry", "23000", 1062);
		ConstraintViolationException hibernateCve =
			new ConstraintViolationException(
				"dup",
				sqlEx,
				"point_history.uk_user_sourceType_sourceId_change_type" // endsWith에 걸리게 일부러 테이블 prefix 포함
			);

		DataIntegrityViolationException dive =
			new DataIntegrityViolationException("could not execute statement", hibernateCve);

		doThrow(dive).when(outboxBusinessTxService).handleAndMarkProcessedTx(eventId);

		// when
		service.processOne(eventId);

		// then
		verify(outboxStatusService).markProcessed(eventId);
		verify(outboxStatusService, never()).markRetry(anyLong(), any());
		verify(outboxStatusService, never()).markFailed(anyLong(), any());
		verifyNoInteractions(outboxEventRepo);
	}

	@Test
	void retryableSql1205_andRetryCountUnder10_marksRetry() throws Exception {
		// given
		SQLException lockTimeout = new SQLException("Lock wait timeout exceeded", "HY000", 1205);
		doThrow(new RuntimeException(lockTimeout))
			.when(outboxBusinessTxService).handleAndMarkProcessedTx(eventId);

		OutboxEvent event = new OutboxEvent(
			AggregateType.ORDER,
			10L,
			EventType.PAYMENT_COMPLETION_GIVE_POINT,
			"{}",
			OutboxStatus.PENDING,
			0
		);
		ReflectionTestUtils.setField(event, "id", eventId);
		when(outboxEventRepo.findById(eventId)).thenReturn(Optional.of(event));

		// when
		service.processOne(eventId);

		// then
		verify(outboxStatusService).markRetry(eq(eventId), any(Throwable.class));
		verify(outboxStatusService, never()).markFailed(anyLong(), any());
	}

	@Test
	void retryableSql1213_andRetryCountAlready10_marksFailed() throws Exception {
		// given
		SQLException deadlock = new SQLException("Deadlock found", "HY000", 1213);
		doThrow(new RuntimeException(deadlock))
			.when(outboxBusinessTxService).handleAndMarkProcessedTx(eventId);

		OutboxEvent event = new OutboxEvent(
			AggregateType.ORDER,
			10L,
			EventType.PAYMENT_COMPLETION_GIVE_POINT,
			"{}",
			OutboxStatus.PENDING,
			10
		);
		ReflectionTestUtils.setField(event, "id", eventId);
		when(outboxEventRepo.findById(eventId)).thenReturn(Optional.of(event));

		// when
		service.processOne(eventId);

		// then
		verify(outboxStatusService).markFailed(eq(eventId), any(Throwable.class));
		verify(outboxStatusService, never()).markRetry(anyLong(), any());
	}

	@Test
	void nonRetryableException_marksFailed() throws Exception {
		// given
		doThrow(new IllegalArgumentException("bad input"))
			.when(outboxBusinessTxService).handleAndMarkProcessedTx(eventId);

		// when
		service.processOne(eventId);

		// then
		verify(outboxStatusService).markFailed(eq(eventId), any(Throwable.class));
		verify(outboxStatusService, never()).markRetry(anyLong(), any());
		verifyNoInteractions(outboxEventRepo);
	}
}