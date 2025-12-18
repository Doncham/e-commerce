package kr.hhplus.be.server.domain.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.hhplus.be.server.entity.BaseTimeEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(
	name = "outbox_event",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "ux_outbox_agg_event",
			columnNames = {"aggregate_type", "aggregate_id", "event_type"}
		)
	}
)
public class OutboxEvent extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "aggregate_type")
	private String aggregateType; // ORDER, POINT_CHARGE etc..
	@Column(name = "aggregate_id")
	private Long aggregateId;
	@Column(name = "event_type")
	private String eventType; // PAYMENT_COMPLETED, POINT_CHARGED etc..
	@Lob
	private String payload;
	@Enumerated(EnumType.STRING)
	private OutboxStatus stats;
	private int retryCount;

	public OutboxEvent(String aggregateType, Long aggregateId, String eventType, String payload, OutboxStatus stats,
		int retryCount) {
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.payload = payload;
		this.stats = stats;
		this.retryCount = retryCount;
	}

	public static OutboxEvent of(String aggregateType,
		Long aggregateId,
		String eventType,
		String payload) {
		return new OutboxEvent(aggregateType, aggregateId, eventType, payload, OutboxStatus.PENDING, 0);
	}

	public void markProcessed() {
		this.stats = OutboxStatus.PROCESSED;
	}
	// 재시도 로직 나중에 추가하기
	public void markFailed() {
		this.stats = OutboxStatus.FAILED;
		this.retryCount++;
	}
}
