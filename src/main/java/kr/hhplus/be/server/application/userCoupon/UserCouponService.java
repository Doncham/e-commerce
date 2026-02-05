package kr.hhplus.be.server.application.userCoupon;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.api.usercoupon.request.UserCouponCreateRequest;
import kr.hhplus.be.server.api.usercoupon.response.UserCouponCreateResponse;
import kr.hhplus.be.server.domain.coupon.Coupon;
import kr.hhplus.be.server.domain.coupon.exception.CouponExpiredException;
import kr.hhplus.be.server.domain.coupon.exception.UserCouponLimitExceededException;
import kr.hhplus.be.server.domain.coupon.exception.InsufficientCouponStockException;
import kr.hhplus.be.server.domain.coupon.exception.NotFoundCoupon;
import kr.hhplus.be.server.domain.usercoupon.UserCoupon;
import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.infrastructure.persistence.coupon.CouponRepository;
import kr.hhplus.be.server.infrastructure.persistence.userCoupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserCouponService {
	private final UserCouponRepository userCouponRepository;
	private final CouponRepository couponRepository;
	@Transactional
	@Retryable(
		retryFor = {
			org.springframework.dao.CannotAcquireLockException.class,
			org.springframework.dao.PessimisticLockingFailureException.class,
		},
		noRetryFor = {
			InsufficientCouponStockException.class,
			CouponExpiredException.class,
			UserCouponLimitExceededException.class,
			NotFoundCoupon.class
		},
		maxAttempts = 3,
		backoff = @Backoff(delay = 50, multiplier = 2.0, random = true),
		exceptionExpression = "@lockRetryPolicy.isMySqlLockWaitTimeout(#root)"
	)
	public UserCouponCreateResponse createUserCoupon(UserCouponCreateRequest request){
		Long userId = request.getUserId();
		Long couponId = request.getCouponId();
		// 분산락을 사용해도 최종 정합성은 DB에서 관리한다.(DB락)
		Coupon coupon = couponRepository.findByIdForUpdate(couponId)
			.orElseThrow(() -> new NotFoundCoupon(ErrorCode.NOT_FOUND_COUPON, couponId));
		Long issuedCountForThisUser = userCouponRepository.countAllByUserIdAndCouponId(userId, couponId);
		// 사용자당 발급 제한 수 초과
		if(issuedCountForThisUser >= coupon.getIssueLimitPerUser()){
			throw new UserCouponLimitExceededException(ErrorCode.COUPON_FOR_USER_LIMIT_EXCEED, couponId, userId);
		}
		// 쿠폰 만료 여부
		if (coupon.isExpired()) {
			throw new CouponExpiredException(ErrorCode.EXPIRED_COUPON, couponId);
		}
		// 쿠폰 재고 수량 체크 및 발급 수 증가
		if(coupon.hasStock()){
			coupon.increaseIssuedCount();
		} else {
			throw new InsufficientCouponStockException(ErrorCode.COUPON_ISSUE_LIMIT_EXCEED, couponId);
		}
		UserCoupon userCoupon = UserCoupon.createUserCoupon(userId, couponId);
		UserCoupon savedUserCoupon = userCouponRepository.save(userCoupon);
		return UserCouponCreateResponse.of(
			savedUserCoupon.getUserId(),
			savedUserCoupon.getId(),
			savedUserCoupon.getCouponId(),
			savedUserCoupon.getStatus(),
			savedUserCoupon.getIssuedAt()
		);
	}
}
