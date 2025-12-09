package kr.hhplus.be.server.domain.coupon;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import kr.hhplus.be.server.entity.BaseTimeEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class Coupon extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	private String code;
	private String name;
	@Enumerated(EnumType.STRING)
	private CouponType type;
	private Long discountValue;
	private Long minOrderAmount;
	private Long issueLimit;
	private Long issuedCount = 0L;
	private Long issueLimitPerUser;
	private LocalDateTime startAt;
	private LocalDateTime endAt;
	private Boolean isActive = true;

	private Coupon(String code, String name, CouponType type, Long discountValue, Long minOrderAmount, Long issueLimit,
		Long issueLimitPerUser, LocalDateTime startAt, LocalDateTime endAt) {
		this.code = code;
		this.name = name;
		this.type = type;
		this.discountValue = discountValue;
		this.minOrderAmount = minOrderAmount;
		this.issueLimit = issueLimit;
		this.issueLimitPerUser = issueLimitPerUser;
		this.startAt = startAt;
		this.endAt = endAt;
	}
	public static Coupon createCoupon(String code, String name, CouponType type, Long discountValue,
		Long minOrderAmount, Long issueLimit, Long issueLimitPerUser, LocalDateTime startAt, LocalDateTime endAt) {
		return new Coupon(code, name, type, discountValue, minOrderAmount, issueLimit, issueLimitPerUser, startAt,
			endAt);
	}

	public boolean isExpired() {
		return endAt.isBefore(LocalDateTime.now());
	}

	public boolean hasStock() {
		return issuedCount < issueLimit;
	}

	public void increaseIssuedCount() {
		this.issuedCount += 1;
	}
}
