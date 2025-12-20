package kr.hhplus.be.server.api.coupon;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.hhplus.be.server.api.usercoupon.request.UserCouponCreateRequest;
import kr.hhplus.be.server.api.usercoupon.response.UserCouponCreateResponse;
import kr.hhplus.be.server.application.userCoupon.UserCouponService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/coupon")
@RequiredArgsConstructor
public class CouponController {
	private final UserCouponService userCouponService;

	@PostMapping("/issue")
	public ResponseEntity<UserCouponCreateResponse> issueCoupon(UserCouponCreateRequest request) {
		UserCouponCreateResponse response = userCouponService.createUserCoupon(request);
		return ResponseEntity.ok(response);

	}
}
