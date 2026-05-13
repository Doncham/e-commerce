package kr.hhplus.be.server.application.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import kr.hhplus.be.server.application.product.dto.PopularProductIncrementPayload;
import kr.hhplus.be.server.domain.dailyProductSale.DailyProductSales;
import kr.hhplus.be.server.domain.dailyProductSale.DailyProductSalesId;
import kr.hhplus.be.server.domain.outbox.AggregateType;
import kr.hhplus.be.server.domain.outbox.EventType;
import kr.hhplus.be.server.domain.outbox.OutboxEvent;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import kr.hhplus.be.server.infrastructure.persistence.dailyproductsales.DailyProductSalesRepository;
import kr.hhplus.be.server.infrastructure.persistence.outbox.OutboxEventRepository;

@SpringBootTest
class OutboxPopularIncrementIntegrationTest {

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	@Autowired
	SingleOutboxProcessService outboxProcessService;

	@Autowired
	OutboxEventRepository outboxEventRepo;

	@Autowired
	DailyProductSalesRepository dailyProductSalesRepo;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	EntityManager em;

	@Autowired
	Clock clock;

	@AfterEach
	void cleanup() {
		outboxEventRepo.deleteAllInBatch();
		dailyProductSalesRepo.deleteAllInBatch();
		em.clear();
	}

	@Test
	void 오늘_이벤트면_dailyProductSales_증가하고_outbox_processed() throws Exception {
		// given
		LocalDate today = LocalDate.now(clock.withZone(KST));

		long orderId = 200L;

		PopularProductIncrementPayload payload = new PopularProductIncrementPayload(
			orderId,
			today,
			List.of(
				new PopularProductIncrementPayload.Item(10L),
				new PopularProductIncrementPayload.Item(10L),
				new PopularProductIncrementPayload.Item(20L)
			)
		);

		OutboxEvent event = savePopularIncrementEvent(orderId, payload);

		// when
		outboxProcessService.processOne(event.getId());

		// then: outbox 상태
		OutboxEvent reloaded = outboxEventRepo.findById(event.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
		assertThat(reloaded.getRetryCount()).isEqualTo(0);

		// then: daily_product_sales 증가 확인

		DailyProductSales row10 = dailyProductSalesRepo.findById(new DailyProductSalesId(today,10L)).orElseThrow();
		DailyProductSales row20 = dailyProductSalesRepo.findById(new DailyProductSalesId(today,20L)).orElseThrow();

		assertThat(row10.getSalesCount()).isEqualTo(2L);
		assertThat(row20.getSalesCount()).isEqualTo(1L);
	}

	@Test
	void 지난날짜_이벤트면_skip되고_outbox_processed된다() throws Exception {
		// given
		LocalDate today = LocalDate.now(clock.withZone(KST));
		LocalDate yesterday = today.minusDays(1);

		// given(clock.withZone(KST)).willReturn(clock);
		// given(clock.instant()).willReturn(today.atStartOfDay(KST).toInstant());

		long orderId = 201L;

		PopularProductIncrementPayload payload = new PopularProductIncrementPayload(
			orderId,
			yesterday,
			List.of(
				new PopularProductIncrementPayload.Item(10L),
				new PopularProductIncrementPayload.Item(20L)
			)
		);

		OutboxEvent event = savePopularIncrementEvent(orderId, payload);

		// when
		outboxProcessService.processOne(event.getId());

		// then: outbox는 processed
		OutboxEvent reloaded = outboxEventRepo.findById(event.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
		assertThat(reloaded.getRetryCount()).isEqualTo(0);

		// then: daily_product_sales에는 아무것도 없어야 함
		assertThat(dailyProductSalesRepo.findById(new DailyProductSalesId(yesterday, 10L))).isEmpty();
		assertThat(dailyProductSalesRepo.findById(new DailyProductSalesId(yesterday, 20L))).isEmpty();
	}

	@Test
	void 같은_오늘이벤트_재처리시_dailyProductSales_중복증가가_발생한다_현재설계() throws Exception {
		// given
		LocalDate today = LocalDate.now(clock.withZone(KST));

		long orderId = 202L;

		PopularProductIncrementPayload payload = new PopularProductIncrementPayload(
			orderId,
			today,
			List.of(
				new PopularProductIncrementPayload.Item(10L)
			)
		);

		OutboxEvent event = savePopularIncrementEvent(orderId, payload);

		// when
		outboxProcessService.processOne(event.getId());
		outboxProcessService.processOne(event.getId());

		// then
		OutboxEvent reloaded = outboxEventRepo.findById(event.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PROCESSED);

		DailyProductSales row10 = dailyProductSalesRepo.findById(new DailyProductSalesId(today, 10L)).orElseThrow();
		assertThat(row10.getSalesCount()).isEqualTo(2L);
	}

	@Test
	void payload_json이_깨져있으면_failed된다() {
		// given
		OutboxEvent broken = OutboxEvent.of(
			AggregateType.ORDER,
			203L,
			EventType.ORDER_PAID_POPULAR_INCREMENT,
			"{ this is not json"
		);
		outboxEventRepo.save(broken);

		// when
		outboxProcessService.processOne(broken.getId());

		// then
		OutboxEvent reloaded = outboxEventRepo.findById(broken.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.FAILED);
		assertThat(reloaded.getRetryCount()).isEqualTo(1);

		assertThat(dailyProductSalesRepo.findAll()).isEmpty();
	}

	private OutboxEvent savePopularIncrementEvent(long orderId, PopularProductIncrementPayload payload) throws Exception {
		String json = objectMapper.writeValueAsString(payload);

		OutboxEvent event = OutboxEvent.of(
			AggregateType.ORDER,
			orderId,
			EventType.ORDER_PAID_POPULAR_INCREMENT,
			json
		);
		return outboxEventRepo.save(event);
	}
}