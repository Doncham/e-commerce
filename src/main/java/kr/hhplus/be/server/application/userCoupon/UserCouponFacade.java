package kr.hhplus.be.server.application.userCoupon;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import kr.hhplus.be.server.api.usercoupon.request.UserCouponCreateRequest;
import kr.hhplus.be.server.api.usercoupon.response.UserCouponCreateResponse;
import kr.hhplus.be.server.domain.usercoupon.exception.CouponIssueBusyException;
import kr.hhplus.be.server.exception.ErrorCode;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserCouponFacade {
	private final RedissonClient redissonClient;
	private final UserCouponService userCouponService;

	public UserCouponCreateResponse createUserCoupons(UserCouponCreateRequest request) {
		// 락 잡기
		Long couponId = request.getCouponId();
		RLock lock = redissonClient.getLock("lock:couponId:" + couponId);
		boolean locked = false;
		try {
			// 최대 200ms 기다리고, 락은 최대 3초 점유(안전하게 자동 해제)
			locked = lock.tryLock(200, 3, TimeUnit.SECONDS);

			if(!locked) {
				// 여기서 429 등 정책에 맞게 응답
				throw new CouponIssueBusyException(ErrorCode.COUPON_ISSUE_BUSY, couponId);
			}

			// 트랜잭션은 서비스 안에서 시작/커밋(@Transactional)
			return userCouponService.createUserCoupon(request);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("락 획득 대기 중 인터럽트", e);
		}
		finally {
			if (locked && lock.isHeldByCurrentThread()) {
				lock.unlock();
			}
		}
	}
}
