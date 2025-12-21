package kr.hhplus.be.server.domain.pointhistory;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.hhplus.be.server.entity.BaseTimeEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(uniqueConstraints = {
	@UniqueConstraint(
		name = "uk_user_sourceType_sourceId_change_type",
		columnNames = {"user_id", "source_type", "source_id", "change_type"}
	)
})
public class PointHistory extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@JoinColumn(name = "user_id", nullable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	private ChangeType changeType; // EARN, USE, CANCEL, EXPIRE

	private Long amount; // +면 적립, -면 사용
	private Long balanceAfterChange;

	@Enumerated(EnumType.STRING)
	private SourceType sourceType; // ORDER, EVENT, ADMIN
	private Long sourceId; // 예: 주문ID

	private PointHistory(Long userId, ChangeType changeType, Long amount, Long balanceAfterChange, SourceType sourceType,
		Long sourceId) {
		this.userId = userId;
		this.changeType = changeType;
		this.amount = amount;
		this.balanceAfterChange = balanceAfterChange;
		this.sourceType = sourceType;
		this.sourceId = sourceId;
	}

	public static PointHistory createPointHistory(Long userId, ChangeType changeType, Long amount, Long balanceAfterChange,
		SourceType sourceType, Long sourceId) {
		return new PointHistory(userId, changeType, amount, balanceAfterChange, sourceType, sourceId);
	}
}
