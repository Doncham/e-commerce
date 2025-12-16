package kr.hhplus.be.server.domain.pointcharge;

import jakarta.persistence.Column;
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
	columnNames = {"user_id", "idempotency_key"}
	)
)
public class PointCharge {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private Long userId;
	private Long amount;
	private Long balanceAfterChange;
	private ChargeStatus status;
	private String idempotencyKey;
	private String failReason;

	private PointCharge(Long userId, Long amount, String idempotencyKey) {
		this.userId = userId;
		this.amount = amount;
		this.idempotencyKey = idempotencyKey;
		this.status = ChargeStatus.PENDING;
	}

	public static PointCharge of(Long userId, Long amount, String idempotencyKey) {
		return new PointCharge(userId, amount, idempotencyKey);
	}

	public void success(Long balanceAfterChange) {
		if(this.status == ChargeStatus.PENDING) {
			this.status = ChargeStatus.SUCCEEDED;
		}
		this.balanceAfterChange = balanceAfterChange;
	}
	public void fail(String failReason) {
		if(this.status == ChargeStatus.PENDING) {
			this.status = ChargeStatus.FAILED;
		}
		this.failReason = failReason;
	}

	public boolean isSuccess() {
		return this.status == ChargeStatus.SUCCEEDED;
	}
	public boolean isFailed() {
		return this.status == ChargeStatus.FAILED;
	}
}
