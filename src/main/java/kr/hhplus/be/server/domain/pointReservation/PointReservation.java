package kr.hhplus.be.server.domain.pointReservation;

import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.hhplus.be.server.domain.common.ReservationReleaseReason;
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

	@Enumerated(EnumType.STRING)
	@Column(name = "release_reason")
	private ReservationReleaseReason releaseReason;

	private PointReservation(
		Long orderId,
		Long userId,
		Long amount
	) {
		validatePositiveAmount(amount);

		this.orderId = Objects.requireNonNull(orderId);
		this.userId = Objects.requireNonNull(userId);
		this.amount = amount;
		this.status = PointReserveStatus.RESERVED;
	}

	public static PointReservation reserve(
		Long orderId,
		Long userId,
		Long amount
	) {
		return new PointReservation(orderId, userId, amount);
	}


	/**
	 * 실제로 RESERVED → RELEASED 전이가 일어나면 true.
	 * 이미 RELEASED라면 멱등 처리를 위해 false.
	 */
	public boolean release(
		ReservationReleaseReason reason
	) {
		if (status == PointReserveStatus.RELEASED) {
			return false;
		}

		if (status == PointReserveStatus.CONFIRMED) {
			throw new IllegalStateException(
				"Cannot release confirmed point reservation. "
					+ "orderId=" + orderId
					+ ", reservationId=" + id
			);
		}

		status = PointReserveStatus.RELEASED;
		releaseReason = reason;

		return true;
	}

	/**
	 * 실제로 RESERVED → CONFIRMED 전이가 일어나면 true.
	 * 이미 CONFIRMED라면 멱등 처리를 위해 false.
	 */
	public boolean confirm() {
		if (status == PointReserveStatus.CONFIRMED) {
			return false;
		}

		if (status == PointReserveStatus.RELEASED) {
			throw new IllegalStateException(
				"Cannot confirm released point reservation. "
					+ "orderId=" + orderId
					+ ", reservationId=" + id
			);
		}

		status = PointReserveStatus.CONFIRMED;
		return true;
	}

	/**
	 * order_id 유니크 제약 때문에 같은 Order에서
	 * 포인트를 다시 예약하면 기존 행을 재활성화해야 한다.
	 * Released, RESERVED 일 때 다시 예약
	 */
	public void reserveAgain(long amount) {
		validatePositiveAmount(amount);

		// RESERVED일 때 예약하는 경우도 있을까? 있지 payment 수정할 때

		if (status == PointReserveStatus.CONFIRMED) {
			throw new IllegalStateException(
				"Cannot reactivate confirmed point reservation. "
					+ "orderId=" + orderId
			);
		}

		this.amount = amount;
		this.status = PointReserveStatus.RESERVED;
		this.releaseReason = null;
	}

	public boolean isReserved() {
		return status == PointReserveStatus.RESERVED;
	}

	private static void validatePositiveAmount(
		long amount
	) {
		if (amount <= 0) {
			throw new IllegalArgumentException(
				"Point reservation amount must be positive. "
					+ "amount=" + amount
			);
		}
	}

	public boolean belongsTo(Long userId) {
		return this.userId.equals(userId);
	}

}
