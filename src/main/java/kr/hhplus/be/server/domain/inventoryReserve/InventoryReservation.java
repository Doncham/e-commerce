package kr.hhplus.be.server.domain.inventoryReserve;

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
		name = "ux_inventory_reservation_order_inventory",
		columnNames = {"order_id", "inventory_id"}
	)
)
public class InventoryReservation extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "order_id", nullable = false)
	private Long orderId;

	@Column(name = "inventory_id", nullable = false)
	private Long inventoryId;

	@Column(nullable = false)
	private Long qty;

	@Enumerated(EnumType.STRING)
	private InventoryReserveStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "release_reason")
	private ReservationReleaseReason releaseReason;

	private InventoryReservation(Long orderId, Long inventoryId, Long qty, InventoryReserveStatus status) {
		validatePositiveQuantity(qty);
		this.orderId = orderId;
		this.inventoryId = inventoryId;
		this.qty = qty;
		this.status = status;
	}

	public static InventoryReservation reserve(Long orderId, Long inventoryId, Long qty) {
		return new InventoryReservation(
			orderId,
			inventoryId,
			qty,
			InventoryReserveStatus.RESERVED
		);
	}

	public boolean release(ReservationReleaseReason failReason) {
		if (this.status == InventoryReserveStatus.RELEASED) return false;
		// CONFIRMED는 release 금지
		if (this.status == InventoryReserveStatus.CONFIRMED) {
			throw new IllegalStateException("Cannot release a confirmed reservation. orderId=" + orderId + ", inventoryId=" + inventoryId);
		}
		this.releaseReason = failReason;
		this.status = InventoryReserveStatus.RELEASED;
		return true;
	}

	public boolean confirm() {
		if(this.status == InventoryReserveStatus.CONFIRMED) return false;

		if(this.status == InventoryReserveStatus.RELEASED) {
			throw new IllegalStateException("Cannot confirm a released reservation. orderId=" + orderId + ", inventoryId=" + inventoryId);
		}

		this.status = InventoryReserveStatus.CONFIRMED;
		return true;
	}

	public void reserveAgain(long qty) {
		validatePositiveQuantity(qty);

		if (status
			== InventoryReserveStatus.CONFIRMED) {
			throw new IllegalStateException(
				"Cannot reactivate confirmed reservation. "
					+ "orderId=" + orderId
					+ ", inventoryId=" + inventoryId
			);
		}

		this.qty = qty;
		this.status = InventoryReserveStatus.RESERVED;
		this.releaseReason = null;
	}

	private static void validatePositiveQuantity(
		long qty
	) {
		if (qty <= 0) {
			throw new IllegalArgumentException(
				"Reservation quantity must be positive. "
					+ "qty=" + qty
			);
		}
	}
}
