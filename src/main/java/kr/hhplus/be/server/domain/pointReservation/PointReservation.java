package kr.hhplus.be.server.domain.pointReservation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.hhplus.be.server.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
	uniqueConstraints = @UniqueConstraint(
		name = "ux_point_reservation_order",
		columnNames = {"order_id"}
	)
)
public class PointReservation extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(name = "order_id", nullable = false)
	private Long orderId;
	@Column(name = "user_id", nullable = false)
	private Long userId;
	@Column(nullable = false)
	private Long amount;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PointReserveStatus status;
	private String failReason;

	private PointReservation(Long orderId, Long userId, Long amount) {
		this.orderId = orderId;
		this.userId = userId;
		this.amount = amount;
		this.status = PointReserveStatus.RESERVED;
	}

	public static PointReservation reserve(Long orderId, Long userId, Long amount) {
		return new PointReservation(orderId, userId, amount);
	}
	public void confirm() {
		if(this.status != PointReserveStatus.RESERVED) return; // 멱등
		this.status = PointReserveStatus.CONFIRMED;
	}
	public void release(String reason) {
		if(this.status != PointReserveStatus.RESERVED) return; // 멱등
		this.status = PointReserveStatus.RELEASED;
		this.failReason = reason;
	}

	public boolean isReserved() {
		return status == PointReserveStatus.RESERVED;
	}
}
