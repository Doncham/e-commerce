package kr.hhplus.be.server.domain.popular_product_snapshot;

import java.time.LocalDateTime;

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
import kr.hhplus.be.server.application.product.PopularDateRange;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(uniqueConstraints = {
	@UniqueConstraint(
		name = "uk_popular_product_snapshot_range_type",
		columnNames = "range_type"
	)
})
public class PopularProductSnapshot {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long snapshotId;
	@Enumerated(EnumType.STRING)
	@Column(name = "range_type", nullable = false)
	private PopularDateRange rangeType;
	@Lob
	@Column(nullable = false)
	private String json;
	@Column(nullable = false)
	private LocalDateTime createdAt;

	public PopularProductSnapshot(PopularDateRange rangeType, String json, LocalDateTime createdAt) {
		this.rangeType = rangeType;
		this.json = json;
		this.createdAt = createdAt;
	}
}
