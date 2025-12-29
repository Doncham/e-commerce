package kr.hhplus.be.server.domain.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.application.point.PointCommandService;
import kr.hhplus.be.server.infrastructure.persistence.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutboxBusinessTxService {
	private final OutboxEventRepository outboxEventRepo;
	private final PointCommandService pointService;
	private final ObjectMapper objectMapper;

	@Transactional
	public void handleAndMarkProcessedTx(Long eventId) throws JsonProcessingException {
		OutboxEvent event = outboxEventRepo.findById(eventId).orElseThrow();

		if (AggregateType.ORDER.equals(event.getAggregateType())
			&& EventType.PAYMENT_COMPLETION_GIVE_POINT.equals(event.getEventType())) {

			PaymentCompletedPayload payload = objectMapper.readValue(event.getPayload(), PaymentCompletedPayload.class);

			pointService.earnForOrder(payload.getUserId(), payload.getOrderId(), payload.getPayAmount());
		}

		// 나중에 쿠폰, 재고 등 다른 이벤트도 여기에서 분기 처리 가능

		event.markProcessed();

	}
}
