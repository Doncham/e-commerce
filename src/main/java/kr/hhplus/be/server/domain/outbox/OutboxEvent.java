package kr.hhplus.be.server.domain.outbox;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
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
			columnNames = {"aggregate_id", "aggregate_type", "event_type"}
		)
	},
	indexes = {
		@Index(name = "ix_outbox_status_nextRetry_id", columnList = "status, next_retry_at, id")
	}
)
public class OutboxEvent extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id")
	private Long id;
	@Column(name = "aggregate_id")
	private Long aggregateId;
	@Column(name = "aggregate_type")
	@Enumerated(EnumType.STRING)
	private AggregateType aggregateType; // ORDER, POINT_CHARGE etc..
	@Column(name = "event_type")
	@Enumerated(EnumType.STRING)
	private EventType eventType; // PAYMENT_COMPLETED, POINT_CHARGED etc..
	@Lob
	private String payload;
	@Enumerated(EnumType.STRING)
	@Column(name = "status")
	private OutboxStatus status;
	private int retryCount;
	@Column(name = "next_retry_at")
	private LocalDateTime nextRetryAt;
	@Lob
	private String lastError;

	public OutboxEvent(AggregateType aggregateType, Long aggregateId, EventType eventType, String payload, OutboxStatus status,
		int retryCount) {
		this.aggregateType = aggregateType;
		this.aggregateId = aggregateId;
		this.eventType = eventType;
		this.payload = payload;
		this.status = status;
		this.retryCount = retryCount;
	}

	public static OutboxEvent of(AggregateType aggregateType,
		Long aggregateId,
		EventType eventType,
		String payload) {
		return new OutboxEvent(aggregateType, aggregateId, eventType, payload, OutboxStatus.PENDING, 0);
	}

	public void markProcessed() {
		if(this.status == OutboxStatus.PROCESSED) return;
		this.status = OutboxStatus.PROCESSED;
		this.lastError = null;
		this.nextRetryAt = null;
	}
	// 재시도 로직 나중에 추가하기
	public void markFailed(Throwable e) {
		this.status = OutboxStatus.FAILED;
		this.retryCount++;
		this.lastError = summarize(e);

	}

	public void markRetry(Throwable e, LocalDateTime nextRetryAt) {
		this.status = OutboxStatus.PENDING;
		this.retryCount++;
		this.nextRetryAt = nextRetryAt;
		this.lastError = summarize(e);
	}
	public static String summarize(Throwable t) {
		Throwable root = rootCause(t);

		String type = root.getClass().getName();
		String msg = String.valueOf(root.getMessage());

		// 너무 길면 잘라 저장
		msg = msg.length() > 800 ? msg.substring(0, 800) + "..." : msg;

		// SQL 예외면 errorCode/SQLState 같이
		if (root instanceof java.sql.SQLException se) {
			return type + " (errorCode=" + se.getErrorCode()
				+ ", sqlState=" + se.getSQLState() + "): " + msg;
		}

		return type + ": " + msg;
	}

	public static Throwable rootCause(Throwable t) {
		Throwable cur = t;
		while (cur != null && cur.getCause() != null && cur.getCause() != cur) {
			cur = cur.getCause();
		}
		return cur != null ? cur : t;
	}
}
