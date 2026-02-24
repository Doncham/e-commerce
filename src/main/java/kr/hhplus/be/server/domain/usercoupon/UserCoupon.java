package kr.hhplus.be.server.domain.usercoupon;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
@Table(
	uniqueConstraints = @UniqueConstraint(
		name = "ux_couponId_and_userId",
		columnNames = {"user_id","coupon_id"}
	)
)
public class UserCoupon {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long userId;
	private Long couponId;
	@Enumerated(EnumType.STRING)
	private UserCouponStatus status;
	private LocalDateTime issuedAt;
	private LocalDateTime usedAt;
	private Long orderId;

	private UserCoupon(Long userId, Long couponId, UserCouponStatus status, LocalDateTime issuedAt, LocalDateTime usedAt,
		Long orderId) {
		this.userId = userId;
		this.couponId = couponId;
		this.status = status;
		this.issuedAt = issuedAt;
		this.usedAt = usedAt;
		this.orderId = orderId;
	}

	public static UserCoupon createUserCoupon(Long userId, Long couponId) {
		return new UserCoupon(userId, couponId, UserCouponStatus.ISSUED, LocalDateTime.now(), null, null);
	}
}
