package kr.hhplus.be.server.api.firstcomecoupon;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import kr.hhplus.be.server.application.firstcomeCoupon.CouponApplyResponse;
import kr.hhplus.be.server.application.firstcomeCoupon.FirstComeCouponService;
import lombok.RequiredArgsConstructor;

@RequestMapping("/api/v1/first-come-coupons")
@RestController
@RequiredArgsConstructor
public class FirstComeCouponController {
	private final FirstComeCouponService service;
	@PostMapping("/apply")
	public ResponseEntity<CouponApplyResponse> create(
		@RequestParam Long userId,
		@RequestParam Long couponId
	) {
		CouponApplyResponse response = service.apply(userId, couponId);
		return ResponseEntity.ok(response);
	}
}
