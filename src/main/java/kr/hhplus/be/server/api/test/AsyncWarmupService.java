package kr.hhplus.be.server.api.test;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import kr.hhplus.be.server.infrastructure.persistence.userCoupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AsyncWarmupService {
	private final UserCouponRepository userCouponRepository;
	@Async("couponIssueExecutor")
	public void warmupAsync(long userId) {
		userCouponRepository.existsById(userId);
	}
}
