package kr.hhplus.be.server.application.outbox;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import jakarta.persistence.EntityManager;
import kr.hhplus.be.server.application.product.PopularProductIncrementPayload;
import kr.hhplus.be.server.domain.outbox.AggregateType;
import kr.hhplus.be.server.domain.outbox.EventType;
import kr.hhplus.be.server.domain.outbox.OutboxEvent;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import kr.hhplus.be.server.infrastructure.persistence.outbox.OutboxEventRepository;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
public class OutboxPopularIncrementIntegrationTest {
	@Autowired
	SingleOutboxProcessService outboxProcessService;

	@Autowired
	OutboxEventRepository outboxEventRepo;

	@Autowired
	ObjectMapper objectMapper;

	@Autowired
	RedisTemplate<String, String> redisTemplate;

	@Autowired
	EntityManager em;

	@AfterEach
	void cleanup() {
		// 1) outbox 정리
		outboxEventRepo.deleteAllInBatch();
		em.clear();

		// 2) redis 정리 (테스트 격리)
		// - 운영에서는 flushdb 절대 금지지만, 통합테스트에서는 가장 깔끔함
		redisTemplate.getConnectionFactory().getConnection().flushDb();
	}

	@Test
	void 성공_인기상품증분_및_outbox_processed_된다() throws Exception {
		// given
		long orderId = 200L;

		PopularProductIncrementPayload payload = new PopularProductIncrementPayload(
			orderId,
			"20260210",
			List.of(
				new PopularProductIncrementPayload.Item(10L, 2L),
				new PopularProductIncrementPayload.Item(20L, 5L)
			)
		);

		OutboxEvent event = savePopularIncrementEvent(orderId, payload);

		// when
		outboxProcessService.processOne(event.getId());

		// then: outbox 상태
		OutboxEvent reloaded = outboxEventRepo.findById(event.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
		assertThat(reloaded.getRetryCount()).isEqualTo(0);

		// then: redis score 검증
		ZSetOperations<String, String> z = redisTemplate.opsForZSet();


		// 7d/30d를 실시간 증분하도록 구현했다는 전제
		assertThat(z.score("rank:7d", "10")).isEqualTo(2.0);
		assertThat(z.score("rank:7d", "20")).isEqualTo(5.0);

		assertThat(z.score("rank:30d", "10")).isEqualTo(2.0);
		assertThat(z.score("rank:30d", "20")).isEqualTo(5.0);
	}

	@Test
	void 같은_이벤트_재처리시_Redis는_유니크가_없으면_중복증분이_발생한다_현재설계동작() throws Exception {
		// given
		long orderId = 201L;

		PopularProductIncrementPayload payload = new PopularProductIncrementPayload(
			orderId,
			"20260210",
			List.of(
				new PopularProductIncrementPayload.Item(10L, 2L)
			)
		);

		OutboxEvent event = savePopularIncrementEvent(orderId, payload);

		// when: 1차 처리
		outboxProcessService.processOne(event.getId());

		// when: 2차 처리(재시도/크래시 윈도우 가정)
		outboxProcessService.processOne(event.getId());

		// then: outbox는 PROCESSED로 보일 가능성이 큼(현재 handle에서 status guard가 없으면 다시 processed 찍힘)
		OutboxEvent reloaded = outboxEventRepo.findById(event.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PROCESSED);

		// then: redis score가 2번 반영되었는지 확인(멱등 장치가 없다면 4가 됨)
		ZSetOperations<String, String> z = redisTemplate.opsForZSet();

		// 7d/30d도 실시간 증분이면 같이 2번 반영
		assertThat(z.score("rank:7d", "10")).isEqualTo(4.0);
		assertThat(z.score("rank:30d", "10")).isEqualTo(4.0);
	}

	@Test
	void payload_json이_깨져있으면_failed() {
		// given
		OutboxEvent broken = OutboxEvent.of(
			AggregateType.ORDER,
			202L,
			EventType.ORDER_PAID_POPULAR_INCREMENT,
			"{ this is not json"
		);
		outboxEventRepo.save(broken);

		// when
		outboxProcessService.processOne(broken.getId());

		// then: outbox failed
		OutboxEvent reloaded = outboxEventRepo.findById(broken.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.FAILED);
		assertThat(reloaded.getRetryCount()).isEqualTo(1);

		// then: redis는 비어있어야 함
		Set<String> keys = redisTemplate.keys("*");
		assertThat(keys).isEmpty();
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
