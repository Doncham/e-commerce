package kr.hhplus.be.server.application.outbox;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import kr.hhplus.be.server.domain.outbox.OutboxEvent;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import kr.hhplus.be.server.infrastructure.persistence.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutboxProcessor {
	private final OutboxEventRepository outboxEventRepo;
	private final SingleOutboxProcessService singleOutboxprocessService;

	public void processOutbox(){
		List<OutboxEvent> events = outboxEventRepo.findByStatusOrderByIdAsc(
			OutboxStatus.PENDING,
			LocalDateTime.now(),
			PageRequest.of(0, 100)
		);
		for (OutboxEvent e : events) {
			singleOutboxprocessService.processOne(e.getId());
		}
	}
}
