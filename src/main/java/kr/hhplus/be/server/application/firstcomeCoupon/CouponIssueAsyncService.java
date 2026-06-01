package kr.hhplus.be.server.application.firstcomeCoupon;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import kr.hhplus.be.server.domain.usercoupon.UserCoupon;
import kr.hhplus.be.server.infrastructure.persistence.userCoupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CouponIssueAsyncService {
	private final UserCouponRepository userCouponRepository;
	private final StringRedisTemplate redis;

	@Async("couponIssueExecutor")
	public void issueAsync(long userId, long couponId) {
		try {
			userCouponRepository.save(UserCoupon.createUserCoupon(userId, couponId));
		} catch (DataIntegrityViolationException e) {
			// 예외 삼키기
			log.info("이미 해당 쿠폰을 발급 받았습니다. userId = {}, couponId = {}", userId, couponId);
		}
		// score를 1로 변경
		redis.opsForZSet().add(CouponRedisKeys.reqKey(couponId), String.valueOf(userId), 1);
	}
}
