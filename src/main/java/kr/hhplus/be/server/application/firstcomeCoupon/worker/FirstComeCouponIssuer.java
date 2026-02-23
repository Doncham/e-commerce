package kr.hhplus.be.server.application.firstcomeCoupon.worker;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.domain.usercoupon.UserCoupon;
import kr.hhplus.be.server.infrastructure.persistence.userCoupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class FirstComeCouponIssuer {
	private final UserCouponRepository userCouponRepository;

	public IssueResult issueToDb(long couponId, long userId) {
		try {
			userCouponRepository.save(UserCoupon.createUserCoupon(userId, couponId));
			return IssueResult.SUCCESS;
		} catch (DataIntegrityViolationException e) {
			// 중복 발급, 해당 쿠폰 없음 등
			log.info("Permanent fail. couponId={}, userId={}, msg={}", couponId, userId, e.getMessage());
			return IssueResult.PERMANENT_FAIL;
		} catch (Exception e) {
			// 그 외의 뭔지 모를 예외
			return IssueResult.RETRYABLE_FAIL;
		}
	}
}
