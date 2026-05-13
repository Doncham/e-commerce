package kr.hhplus.be.server.domain.outbox;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.application.point.PointService;
import kr.hhplus.be.server.application.product.dto.PopularProductIncrementPayload;
import kr.hhplus.be.server.infrastructure.persistence.dailyproductsales.DailyProductSalesRepository;
import kr.hhplus.be.server.infrastructure.persistence.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxBusinessTxService {
	private final OutboxEventRepository outboxEventRepo;
	private final PointService pointService;
	private final ObjectMapper objectMapper;
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private final Clock clock;

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
			LocalDate closedDate = LocalDate.now(clock.withZone(KST));
			LocalDate salesDate = payload.getSalesDate();
			// 어제 이벤트면 처리 x
			if(closedDate.isAfter(salesDate)) {
				event.markProcessed();
				log.info("skip old popular increment event. salesDate={}, eventId={}", salesDate, eventId);
				return;
			}

			// 리팩토링
			// dailyProductSales upsert하기
			for (PopularProductIncrementPayload.Item item : payload.getItems()) {
				Long productId = item.getProductId();
				dailyProductSalesRepo.increasePaidCount(salesDate, productId);
			}
		}

		event.markProcessed();

	}
}
