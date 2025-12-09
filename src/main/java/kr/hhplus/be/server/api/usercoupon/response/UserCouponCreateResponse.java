package kr.hhplus.be.server.api.usercoupon.response;

import java.time.LocalDateTime;

import kr.hhplus.be.server.domain.usercoupon.UserCouponStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserCouponCreateResponse {
	private Long userId;
	private Long userCouponId;
	private Long couponId;
	private UserCouponStatus status;
	private LocalDateTime issuedAt;

	private UserCouponCreateResponse(Long userId, Long userCouponId, Long couponId, UserCouponStatus status, LocalDateTime issuedAt) {
		this.userId = userId;
		this.userCouponId = userCouponId;
		this.couponId = couponId;
		this.status = status;
		this.issuedAt = issuedAt;
	}

	public static UserCouponCreateResponse of(Long userId, Long userCouponId, Long couponId, UserCouponStatus status, LocalDateTime issuedAt) {
		return new UserCouponCreateResponse(userId, userCouponId, couponId, status, issuedAt);
	}
}
