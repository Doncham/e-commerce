package kr.hhplus.be.server.domain.outbox;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.application.point.PointService;
import kr.hhplus.be.server.application.product.PopularProductIncrementPayload;
import kr.hhplus.be.server.application.product.PopularRankPort;
import kr.hhplus.be.server.domain.dailyProductSale.DailyProductSalesRepository;
import kr.hhplus.be.server.infrastructure.persistence.outbox.OutboxEventRepository;
import kr.hhplus.be.server.infrastructure.persistence.popularproductsnapshot.PopularProductSnapshotRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OutboxBusinessTxService {
	private final OutboxEventRepository outboxEventRepo;
	private final PointService pointService;
	private final ObjectMapper objectMapper;

	private final DailyProductSalesRepository dailyProductSalesRepo;

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

			// 리팩토링
			// dailyProductSales upsert하기
			LocalDate salesDate = payload.getSalesDate();
			for (PopularProductIncrementPayload.Item item : payload.getItems()) {
				Long productId = item.getProductId();
				dailyProductSalesRepo.increasePaidCount(salesDate, productId);
			}
		}

		event.markProcessed();

	}
}
