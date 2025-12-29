package kr.hhplus.be.server.application.outbox;

import static org.assertj.core.api.Assertions.*;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import kr.hhplus.be.server.domain.outbox.AggregateType;
import kr.hhplus.be.server.domain.outbox.EventType;
import kr.hhplus.be.server.domain.outbox.OutboxEvent;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;
import kr.hhplus.be.server.domain.outbox.PaymentCompletedPayload;
import kr.hhplus.be.server.infrastructure.persistence.outbox.OutboxEventRepository;
import kr.hhplus.be.server.infrastructure.persistence.point.PointRepository;
import kr.hhplus.be.server.infrastructure.persistence.pointhistory.PointHistoryRepository;

@SpringBootTest
public class OutboxPointEarnIntegrationTest {
	@Autowired
	SingleOutboxProcessService outboxProcessService;
	@Autowired
	OutboxEventRepository outboxEventRepo;

	@Autowired
	PointRepository pointRepository;
	@Autowired
	PointHistoryRepository pointHistoryRepository;

	@Autowired
	ObjectMapper objectMapper;
	@Autowired
	EntityManager em;

	@AfterEach
	void cleanup() {
		// REQUIRES_NEW 커밋이 남기 때문에, 테스트 종료마다 직접 정리
		pointHistoryRepository.deleteAllInBatch();
		pointRepository.deleteAllInBatch();
		outboxEventRepo.deleteAllInBatch();
		em.clear();
	}

	@Test
	void 성공_포인트적립_및_outbox_processed_된다() throws Exception {
		// given
		long userId = 1L;
		long orderId = 100L;
		long payAmount = 30_000L;

		OutboxEvent event = savePaymentGivePointEvent(userId, orderId, payAmount);

		// when
		outboxProcessService.processOne(event.getId());

		// then
		OutboxEvent reloaded = outboxEventRepo.findById(event.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
		assertThat(reloaded.getRetryCount()).isEqualTo(0);

		// 포인트/히스토리 생성 검증
		Long pointBalance = (Long) em.createQuery("""
            select p.balance
            from Point p
            where p.userId = :userId
        """).setParameter("userId", userId)
			.getSingleResult();

		Long historyCount = (Long) em.createQuery("""
            select count(ph)
            from PointHistory ph
            where ph.userId = :userId
              and ph.sourceType = 'ORDER'
              and ph.sourceId = :orderId
              and ph.changeType = 'EARN'
        """).setParameter("userId", userId)
			.setParameter("orderId", orderId)
			.getSingleResult();

		assertThat(historyCount).isEqualTo(1);
		assertThat(pointBalance).isGreaterThan(0L);
	}

	@Test
	void 같은_이벤트_재처리시_유니크중복이면_이미처리됨으로_processed_유지되고_포인트_추가적립_안된다() throws Exception {
		// given
		long userId = 1L;
		long orderId = 101L;
		long payAmount = 50_000L;

		OutboxEvent event = savePaymentGivePointEvent(userId, orderId, payAmount);

		// when: 1차 처리
		outboxProcessService.processOne(event.getId());

		Long balanceAfterFirst = (Long) em.createQuery("""
            select p.balance from Point p where p.userId = :userId
        """).setParameter("userId", userId).getSingleResult();

		// when: 2차 처리(재시도/크래시 윈도우 가정)
		outboxProcessService.processOne(event.getId());

		// then: outbox는 PROCESSED 유지
		OutboxEvent reloaded = outboxEventRepo.findById(event.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.PROCESSED);
		assertThat(reloaded.getRetryCount()).isEqualTo(0); // 중복은 실패가 아니라 이미 처리됨

		// then: 포인트 잔액 증가 없음, 히스토리 추가 없음
		Long balanceAfterSecond = (Long) em.createQuery("""
            select p.balance from Point p where p.userId = :userId
        """).setParameter("userId", userId).getSingleResult();

		Long historyCount = (Long) em.createQuery("""
            select count(ph)
            from PointHistory ph
            where ph.userId = :userId
              and ph.sourceType = 'ORDER'
              and ph.sourceId = :orderId
              and ph.changeType = 'EARN'
        """).setParameter("userId", userId)
			.setParameter("orderId", orderId)
			.getSingleResult();

		assertThat(balanceAfterSecond).isEqualTo(balanceAfterFirst);
		assertThat(historyCount).isEqualTo(1);
	}

	@Test
	void 도메인예외_발생시_outbox_failed_되고_포인트적립은_롤백된다() throws Exception {
		// given
		long userId = 1L;
		long orderId = 102L;
		long payAmount = 0L; // 도메인 예외 유발 (payAmount <= 0)

		OutboxEvent event = savePaymentGivePointEvent(userId, orderId, payAmount);

		// when
		outboxProcessService.processOne(event.getId());

		// then
		OutboxEvent reloaded = outboxEventRepo.findById(event.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.FAILED);
		assertThat(reloaded.getRetryCount()).isEqualTo(1);

		// 포인트/히스토리 생성 안 됨
		Long pointCount = (Long) em.createQuery("""
            select count(p) from Point p where p.userId = :userId
        """).setParameter("userId", userId).getSingleResult();

		Long historyCount = (Long) em.createQuery("""
            select count(ph)
            from PointHistory ph
            where ph.userId = :userId
              and ph.sourceType = 'ORDER'
              and ph.sourceId = :orderId
              and ph.changeType = 'EARN'
        """).setParameter("userId", userId)
			.setParameter("orderId", orderId)
			.getSingleResult();

		assertThat(pointCount).isEqualTo(0);
		assertThat(historyCount).isEqualTo(0);
	}

	@Test
	void payload_json이_깨져있으면_failed_되고_부작용은_없다() {
		// given
		OutboxEvent broken = OutboxEvent.of(
			AggregateType.ORDER,
			103L,
			EventType.PAYMENT_COMPLETION_GIVE_POINT,
			"{ this is not json"
		);
		outboxEventRepo.save(broken);

		// when
		outboxProcessService.processOne(broken.getId());

		// then
		OutboxEvent reloaded = outboxEventRepo.findById(broken.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(OutboxStatus.FAILED);
		assertThat(reloaded.getRetryCount()).isEqualTo(1);

		assertThat(pointRepository.count()).isEqualTo(0);
		assertThat(pointHistoryRepository.count()).isEqualTo(0);
	}

	private OutboxEvent savePaymentGivePointEvent(long userId, long orderId, long payAmount) throws Exception {
		PaymentCompletedPayload payload = PaymentCompletedPayload.of(userId, orderId, payAmount, "pg-123");

		String json = objectMapper.writeValueAsString(payload);

		OutboxEvent event = OutboxEvent.of(
			AggregateType.ORDER,
			orderId,
			EventType.PAYMENT_COMPLETION_GIVE_POINT,
			json
		);
		return outboxEventRepo.save(event);
	}
}
