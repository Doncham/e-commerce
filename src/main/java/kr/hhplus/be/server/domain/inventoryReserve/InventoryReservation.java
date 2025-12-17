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
import kr.hhplus.be.server.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
	uniqueConstraints = @UniqueConstraint(
		name = "ux_orderId_and_productId",
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
	private Long qty;
	@Enumerated(EnumType.STRING)
	private InventoryReserveStatus status;
	private String failReason;

	private InventoryReservation(Long orderId, Long inventoryId, Long qty, InventoryReserveStatus status) {
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

	public void release(String failReason) {
		if (this.status == InventoryReserveStatus.RELEASED) return;
		// CONFIRMED는 release 금지
		if (this.status == InventoryReserveStatus.CONFIRMED) {
			throw new IllegalStateException("Cannot release a confirmed reservation. orderId=" + orderId + ", inventoryId=" + inventoryId);
		}
		this.failReason = failReason;
		this.status = InventoryReserveStatus.RELEASED;
	}

	public void confirm() {
		if(this.status == InventoryReserveStatus.CONFIRMED) return;

		if(this.status == InventoryReserveStatus.RELEASED) {
			throw new IllegalArgumentException("Cannot confirm a released reservation. orderId=" + orderId + ", inventoryId=" + inventoryId);
		}

		this.status = InventoryReserveStatus.CONFIRMED;
	}
}
