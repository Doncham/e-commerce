package kr.hhplus.be.server.application.payment;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.application.product.PopularProductIncrementPayload;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.outbox.AggregateType;
import kr.hhplus.be.server.domain.outbox.EventType;
import kr.hhplus.be.server.domain.outbox.OutboxEvent;
import kr.hhplus.be.server.domain.outbox.PaymentCompletedPayload;
import kr.hhplus.be.server.infrastructure.persistence.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentOutboxPublisher {
	private final OutboxEventRepository outboxEventRepository;
	private final ObjectMapper objectMapper;
	private static final ZoneId KST = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");

	public void publishPaymentSuccess(Order order, String pgTransactionId, LocalDateTime paidAt) {
		publishPointEarn(order, pgTransactionId);
		publishPopularIncrement(order, paidAt);
	}

	public void publishPointEarn(Order order, String pgTransactionId) {
		PaymentCompletedPayload payload = PaymentCompletedPayload.of(
			order.getUser().getId(),
			order.getId(),
			order.getPayAmount(),
			pgTransactionId
		);

		saveOutbox(AggregateType.ORDER, order.getId(), EventType.PAYMENT_COMPLETION_GIVE_POINT, payload);
	}

	public void publishPopularIncrement(Order order, LocalDateTime paidAt) {
		// 1) 날짜 문자열 생성 (KST 기준)
		String yyyymmdd = ZonedDateTime.of(paidAt, KST).format(YYYYMMDD);

		// 2) 주문 아이템(productId, qty) 구성
		List<PopularProductIncrementPayload.Item> items = order.getOrderProducts().stream()
			.map(op -> new PopularProductIncrementPayload.Item(op.getProductId(), op.getQty()))
			.toList();

		PopularProductIncrementPayload payload = new PopularProductIncrementPayload(
			order.getId(),
			yyyymmdd,
			items
		);

		saveOutbox(AggregateType.ORDER, order.getId(), EventType.ORDER_PAID_POPULAR_INCREMENT, payload);
	}

	private void saveOutbox(AggregateType aggregateType, Long aggregateId, EventType eventType, Object payload) {
		try {
			String payloadJson = objectMapper.writeValueAsString(payload);
			OutboxEvent outboxEvent = OutboxEvent.of(aggregateType, aggregateId, eventType, payloadJson);
			outboxEventRepository.save(outboxEvent);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Failed to serialize outbox payload", e);
		}
	}
}
