package kr.hhplus.be.server.domain.outbox;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.infrastructure.persistence.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutboxStatusService {
	private final OutboxEventRepository outboxEventRepo;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markProcessed(Long eventId) {
		OutboxEvent event = outboxEventRepo.findById(eventId).orElseThrow();
		event.markProcessed();
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markRetry(Long eventId, Throwable e) {
		OutboxEvent event = outboxEventRepo.findById(eventId).orElseThrow();
		int nextCount = event.getRetryCount() + 1;
		LocalDateTime nextRetryAt = BackoffPolicy.nextRetryAt(nextCount);
		event.markRetry(e, nextRetryAt);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markFailed(Long eventId, Throwable e) {
		OutboxEvent event = outboxEventRepo.findById(eventId).orElseThrow();
		event.markFailed(e);
	}
}
