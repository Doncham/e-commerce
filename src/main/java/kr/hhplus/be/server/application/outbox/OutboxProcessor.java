package kr.hhplus.be.server.application.outbox;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.application.point.PointCommandService;
import kr.hhplus.be.server.domain.outbox.OutboxEvent;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import kr.hhplus.be.server.domain.outbox.PaymentCompletedPayload;
import kr.hhplus.be.server.infrastructure.persistence.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutboxProcessor {
	private final OutboxEventRepository outboxEventRepository;
	private final ObjectMapper objectMapper;
	private final PointCommandService pointService;
	@Transactional
	public void processOutbox(){
		List<OutboxEvent> events = outboxEventRepository.findTop100ByStatusOrderByIdAsc(
			OutboxStatus.PENDING);

		for (OutboxEvent event : events) {
			try {
				handleEvent(event);
				event.markProcessed();
			} catch (Exception e) {
				event.markFailed();
				// 로그만 남기거나, 재시도 정책을 고민할 수 있다.
			}
		}
	}

	private void handleEvent(OutboxEvent event) throws JsonProcessingException {
		if ("ORDER".equals(event.getAggregateType())
		&& "PAYMENT_COMPLETED".equals(event.getEventType())) {
			PaymentCompletedPayload payload = objectMapper.readValue(event.getPayload(),
				PaymentCompletedPayload.class);

			// 원래 PaymentService에서 바로 하던 포인트 적립 로직을 여기로 옮긴다.
			pointService.earnForOrder(
				payload.getUserId(),
				payload.getOrderId(),
				payload.getPayAmount()
			);
		}
		// 나중에 쿠폰, 재고 등 다른 이벤트도 여기에서 분기 처리 가능
	}
}
