package kr.hhplus.be.server.application.firstcomeCoupon.worker;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import kr.hhplus.be.server.application.firstcomeCoupon.ActiveCouponRegistry;
import kr.hhplus.be.server.domain.usercoupon.UserCoupon;
import kr.hhplus.be.server.infrastructure.persistence.userCoupon.UserCouponRepository;

@SpringBootTest(
	properties = {
		// ✅ 테스트에서 스케줄러가 자동 실행되지 않게 차단
		"spring.task.scheduling.enabled=false"
	}
)
@ActiveProfiles("test")
class FirstComeCouponWorkerIntegrationTest {

	private static final long COUPON_ID = 1L;
	private static final Duration TTL = Duration.ofDays(2);

	@Autowired FirstComeCouponWorker worker;
	@Autowired ActiveCouponRegistry activeCouponRegistry;
	@Autowired StringRedisTemplate redis;
	@Autowired UserCouponRepository userCouponRepository;
	@MockitoSpyBean
	FirstComeCouponIssuer issuer;


	private String reqKey(long couponId)   { return "coupon:" + couponId + ":req"; }
	private String remainKey(long couponId){ return "coupon:" + couponId + ":remain"; }
	private String issuedKey(long couponId){ return "coupon:" + couponId + ":issued"; }

	@BeforeEach
	void setUp() {
		// DB clean
		userCouponRepository.deleteAll();

		// Redis clean
		redis.delete("coupon:active");
		redis.delete(reqKey(COUPON_ID));
		redis.delete(remainKey(COUPON_ID));
		redis.delete(issuedKey(COUPON_ID));

		// active 등록
		activeCouponRegistry.addActiveCoupon(COUPON_ID);

		// remain 초기화
		redis.opsForValue().set(remainKey(COUPON_ID), "3", TTL);
	}

	@AfterEach
	void tearDown() {
		// Redis clean
		redis.delete("coupon:active");
		redis.delete(reqKey(COUPON_ID));
		redis.delete(remainKey(COUPON_ID));
		redis.delete(issuedKey(COUPON_ID));

		// DB clean
		userCouponRepository.deleteAll();

		// spy 초기화
		reset(issuer);
	}

	@Test
	void processCoupon_success_issuesUpToRemain_andLeavesRestInQueue() {
		// given: 요청 5명, 재고 3장
		redis.opsForZSet().add(reqKey(COUPON_ID), "101", 1.0);
		redis.opsForZSet().add(reqKey(COUPON_ID), "102", 2.0);
		redis.opsForZSet().add(reqKey(COUPON_ID), "103", 3.0);
		redis.opsForZSet().add(reqKey(COUPON_ID), "104", 4.0);
		redis.opsForZSet().add(reqKey(COUPON_ID), "105", 5.0);

		// when
		worker.processCoupon(COUPON_ID, 200);

		// then: DB 발급 3개
		assertThat(userCouponRepository.count()).isEqualTo(3);

		// remain = 0
		String remain = redis.opsForValue().get(remainKey(COUPON_ID));
		assertThat(Long.parseLong(remain)).isEqualTo(0L);

		// req에는 2명 남음
		Long reqSize = redis.opsForZSet().size(reqKey(COUPON_ID));
		assertThat(reqSize).isEqualTo(2L);
	}

	@Test
	void processCoupon_permanentFail_doesNotRequeue_andRestoresRemain() {
		// given: 재고 1장, 요청 1명
		redis.opsForValue().set(remainKey(COUPON_ID), "1", TTL);
		redis.opsForZSet().add(reqKey(COUPON_ID), "201", 10.0);

		// 미리 같은 (couponId,userId) 발급 기록을 넣어서 유니크 충돌 유도
		userCouponRepository.save(UserCoupon.createUserCoupon(201L, COUPON_ID));

		// when
		worker.processCoupon(COUPON_ID, 200);

		// then: 추가 발급 없음
		assertThat(userCouponRepository.count()).isEqualTo(1);

		// PERMANENT_FAIL이면 requeue 안 함 -> req는 비어야 함
		Long reqSize = redis.opsForZSet().size(reqKey(COUPON_ID));
		assertThat(reqSize).isEqualTo(0L);

		// 발급 실패했으니 remain은 1로 복구되어야 함
		String remain = redis.opsForValue().get(remainKey(COUPON_ID));
		assertThat(remain).isNotNull();
		assertThat(Long.parseLong(remain)).isEqualTo(1L);
	}

	@Test
	void processCoupon_retryableFail_requeues_withBackoff_andRestoresRemain() {
		// given: 재고 1장, 요청 1명(score=100)
		redis.opsForValue().set(remainKey(COUPON_ID), "1", TTL);
		redis.opsForZSet().add(reqKey(COUPON_ID), "301", 100.0);

		// issuer를 강제로 RETRYABLE_FAIL 반환하게 스텁
		doReturn(IssueResult.RETRYABLE_FAIL)
			.when(issuer).issueToDb(COUPON_ID, 301L);

		// when
		worker.processCoupon(COUPON_ID, 5);

		// then: DB 발급 없음
		assertThat(userCouponRepository.count()).isEqualTo(0);

		// remain 복구 -> 1
		String remain = redis.opsForValue().get(remainKey(COUPON_ID));
		assertThat(Long.parseLong(remain)).isEqualTo(1L);

		// requeue 되었으니 req size는 1
		Long reqSize = redis.opsForZSet().size(reqKey(COUPON_ID));
		assertThat(reqSize).isEqualTo(1L);

		// backoff(+5000) 반영 확인: score가 100보다 커야 함
		Double newScore = redis.opsForZSet().score(reqKey(COUPON_ID), "301");
		assertThat(newScore).isNotNull();
		assertThat(newScore).isGreaterThan(100.0);
	}
}