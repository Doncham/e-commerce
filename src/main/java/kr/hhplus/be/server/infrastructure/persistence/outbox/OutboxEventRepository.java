package kr.hhplus.be.server.infrastructure.persistence.outbox;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.domain.outbox.AggregateType;
import kr.hhplus.be.server.domain.outbox.EventType;
import kr.hhplus.be.server.domain.outbox.OutboxEvent;
import kr.hhplus.be.server.domain.outbox.OutboxStatus;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
	@Query("""
		select e
		from OutboxEvent e
		where e.status = :status
			and (e.nextRetryAt is null or e.nextRetryAt <= :now)
		order by e.id asc
	""")
	List<OutboxEvent> findByStatusOrderByIdAsc(
		@Param("status") OutboxStatus status,
		@Param("now") LocalDateTime now,
		Pageable pageable);

	Optional<OutboxEvent> findByAggregateIdAndAggregateTypeAndEventType(
		Long aggregateId,
		AggregateType aggregateType,
		EventType eventType
	);

	Long countByAggregateIdAndAggregateType(Long aggregateId, AggregateType aggregateType);

}
