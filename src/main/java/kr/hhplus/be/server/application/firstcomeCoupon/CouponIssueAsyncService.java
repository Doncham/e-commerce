package kr.hhplus.be.server.application.firstcomeCoupon;

import static kr.hhplus.be.server.application.firstcomeCoupon.metric.CouponIssueMetricResult.*;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import kr.hhplus.be.server.application.firstcomeCoupon.metric.CouponIssueMetricResult;
import kr.hhplus.be.server.application.firstcomeCoupon.metric.CouponIssueMetrics;
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
	private final CouponIssueMetrics couponIssueMetrics;

	@Async("couponIssueExecutor")
	public void issueAsync(long userId, long couponId) {
		CouponIssueMetricResult dbResult;
		//System.out.println("AsyncCouponThread: " + Thread.currentThread().getId() +", || userId: " + userId + ". || couponId:" + couponId);
		try {
			userCouponRepository.save(UserCoupon.createUserCoupon(userId, couponId));
			dbResult = SUCCESS;
		} catch (DataIntegrityViolationException e) {
			dbResult = DUPLICATE;
			// 예외 삼키기
			log.debug("이미 해당 쿠폰을 발급 받았습니다. userId = {}, couponId = {}", userId, couponId);
		} catch (RuntimeException e) {
			couponIssueMetrics.increment(DB_ERROR);
			throw e;
		}

		try {
			redis.opsForZSet().add(
				CouponRedisKeys.reqKey(couponId),
				String.valueOf(userId),
				1
			);

		} catch (RuntimeException e) {
			couponIssueMetrics.increment(REDIS_MARK_ERROR);
			throw e;
		}

		couponIssueMetrics.increment(dbResult);
	}
}
