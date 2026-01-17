package kr.hhplus.be.server.application.userCoupon;

import org.springframework.stereotype.Service;

import kr.hhplus.be.server.api.usercoupon.request.UserCouponCreateRequest;
import kr.hhplus.be.server.api.usercoupon.response.UserCouponCreateResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserCouponFacade {
	private final CouponLockExecutor lockExecutor;
	private final UserCouponService userCouponService;

	public UserCouponCreateResponse createUserCoupons(UserCouponCreateRequest request) {
		return lockExecutor.execute(request.getCouponId(),
			() -> userCouponService.createUserCoupon(request));
	}
}
