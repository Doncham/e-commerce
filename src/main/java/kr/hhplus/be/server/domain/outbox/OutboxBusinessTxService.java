package kr.hhplus.be.server.domain.outbox;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.application.point.PointCommandService;
import kr.hhplus.be.server.application.product.PopularProductIncrementPayload;
import kr.hhplus.be.server.application.product.PopularRankPort;
import kr.hhplus.be.server.infrastructure.persistence.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutboxBusinessTxService {
	private final OutboxEventRepository outboxEventRepo;
	private final PointCommandService pointService;
	private final ObjectMapper objectMapper;

	private final PopularRankPort popularRankPort;

	@Transactional
	public void handleAndMarkProcessedTx(Long eventId) throws JsonProcessingException {
		OutboxEvent event = outboxEventRepo.findById(eventId).orElseThrow();
		// 1) 포인트 적립
		if (AggregateType.ORDER.equals(event.getAggregateType())
			&& EventType.PAYMENT_COMPLETION_GIVE_POINT.equals(event.getEventType())) {

			PaymentCompletedPayload payload = objectMapper.readValue(event.getPayload(), PaymentCompletedPayload.class);

			pointService.earnForOrder(payload.getUserId(), payload.getOrderId(), payload.getPayAmount());
		}

		// 2) 인기상품 증분 (Redis)
		if (AggregateType.ORDER.equals(event.getAggregateType())
			&& EventType.ORDER_PAID_POPULAR_INCREMENT.equals(event.getEventType())) {

			PopularProductIncrementPayload payload = objectMapper.readValue(event.getPayload(),
				PopularProductIncrementPayload.class);

			// 이 작업 후 장애 발생 시 redis 증분이 여러번 집계될 수 있다.
			// 00:00시에 동작하는 배치를 통해 최종적인 정합성을 맞출 계획.
			for (PopularProductIncrementPayload.Item item : payload.getItems()) {
				popularRankPort.incrementDaily(payload.getYyyymmdd(), item.getProductId(), item.getQty());

				popularRankPort.increment7d(item.getProductId(), item.getQty());
				popularRankPort.increment30d(item.getProductId(), item.getQty());
			}

			event.markProcessed();
		}

		// 나중에 쿠폰, 재고 등 다른 이벤트도 여기에서 분기 처리 가능

		event.markProcessed();

	}
}
