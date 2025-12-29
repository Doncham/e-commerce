package kr.hhplus.be.server.domain.outbox;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import kr.hhplus.be.server.application.outbox.OutboxProcessor;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OutboxScheduler {
	private final OutboxProcessor processor;

	@Scheduled(fixedDelayString = "PT1S") // 1초마다, 끝나고 1초 후 재실행
	public void run() {
		processor.processOutbox();
	}
}
