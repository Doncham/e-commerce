package kr.hhplus.be.server.infrastructure.persistence.outbox;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.domain.outbox.AggregateType;
import kr.hhplus.be.server.domain.outbox.EventType;
import kr.hhplus.be.server.domain.outbox.OutboxEvent;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
	List<OutboxEvent> findTop100ByStatusOrderByIdAsc(OutboxStatus status);

	Optional<OutboxEvent> findByAggregateIdAndAggregateTypeAndEventType(
		Long aggregateId,
		AggregateType aggregateType,
		EventType eventType
	);

	Long countByAggregateIdAndAggregateType(Long aggregateId, AggregateType aggregateType);
}
