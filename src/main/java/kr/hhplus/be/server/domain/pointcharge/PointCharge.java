package kr.hhplus.be.server.domain.pointcharge;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(uniqueConstraints = @UniqueConstraint(
	name = "uk_point_charge_user_id_idempotency_key",
	columnNames = {"userId", "idempotencyKey"}
	)
)
public class PointCharge {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private Long userId;
	private Long amount;
	private ChargeStatus status;
	private String idempotencyKey;

	private PointCharge(Long userId, Long amount, String idempotencyKey) {
		this.userId = userId;
		this.amount = amount;
		this.idempotencyKey = idempotencyKey;
		this.status = ChargeStatus.REQUESTED;
	}

	public static PointCharge of(Long userId, Long amount, String idempotencyKey) {
		return new PointCharge(userId, amount, idempotencyKey);
	}
}
