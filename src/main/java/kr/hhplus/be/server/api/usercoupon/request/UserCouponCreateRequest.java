package kr.hhplus.be.server.api.usercoupon.request;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserCouponCreateRequest {
	private Long userId;
	private Long couponId;

	private UserCouponCreateRequest(Long userId, Long couponId) {
		this.userId = userId;
		this.couponId = couponId;
	}

	public static UserCouponCreateRequest of(Long userId, Long couponId) {
		return new UserCouponCreateRequest(userId, couponId);
	}
}
