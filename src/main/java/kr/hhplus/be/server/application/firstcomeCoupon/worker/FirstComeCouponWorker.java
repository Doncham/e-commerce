package kr.hhplus.be.server.application.firstcomeCoupon.worker;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import kr.hhplus.be.server.application.firstcomeCoupon.CouponRedisKeys;
import kr.hhplus.be.server.domain.usercoupon.UserCoupon;
import kr.hhplus.be.server.infrastructure.persistence.userCoupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FirstComeCouponWorker {
	private final UserCouponRepository userCouponRepository;
	private final StringRedisTemplate redis;
	private final Clock clock;
	private final RedissonClient redisson;

	private static final Integer POP_SIZE = 10;
	private static final Integer MOVE_SIZE = 100;

	@Scheduled(fixedDelay = 1000)
	public void issueCoupon() {
		// event 진행 중인 couponId들을 가져오기
		Set<String> couponIdSet = redis.opsForSet().members(CouponRedisKeys.eventCouponKey());

		if(couponIdSet == null || couponIdSet.isEmpty()) return;

		for (String couponId : couponIdSet) {
			List<String> userIds =  popUsers(Integer.parseInt(couponId));

			// 락 잡고 옮기기
			if(userIds.isEmpty()) {
				RLock lock = redisson.getLock(CouponRedisKeys.lockKey(Integer.parseInt(couponId)));
				boolean locked = false;
				try {
					// waitTime = 0, leaseTime = 30 -> 워커가 죽어도 30초 뒤에는 락을 해제함.
					locked = lock.tryLock(0, 30, TimeUnit.SECONDS);
					if (!locked) {
						// 다른 이벤트 쿠폰 처리, continue를 하더라도 finally는 실행되는건가?
						continue;
					}
					// zset:req -> zset:pop으로 100개 복사하기
					copyRequests(Integer.parseInt(couponId));
					// 쿠폰 발급을 실행하지 않고 copy만 수행. 왜냐면 다른 이벤트 쿠폰도 빠르게 복사가 필요함.
					continue;
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					log.warn("Interrupted while acquiring lock. couponId={}", couponId, e);
					return;
				} finally {
					if (locked && lock.isHeldByCurrentThread()) {
						lock.unlock();
					}
				}
			}

			// 쿠폰 발급 처리하기
			for(String userId : userIds) {
				// 여기서 unique 예외 발생 시 전체 loop가 망가진다.
				try{
					userCouponRepository.save(
						UserCoupon.createUserCoupon(
							Long.parseLong(userId),
							Long.parseLong(couponId)
						)
					);
					redis.opsForZSet().add(CouponRedisKeys.reqKey(Integer.parseInt(couponId)), userId, 1);
				} catch (DataIntegrityViolationException e) {
					// 중복 예외가 만약에 터졌다면 score 변경을 안했을 때 계속 재시도 대상이 될 수 있다.
					log.info("Already issued coupon. couponId={}, userId={}", couponId, userId);
					redis.opsForZSet().add(CouponRedisKeys.reqKey(Integer.parseInt(couponId)), userId, 1);
				} catch (Exception e) {
					log.warn("Failed to issue coupon. couponId={}, userId={}", couponId, userId, e);
				}

			}
		}

	}

	private void copyRequests(Integer couponId) {
		Set<ZSetOperations.TypedTuple<String>> couponRequests = redis.opsForZSet().rangeByScoreWithScores(
			CouponRedisKeys.reqKey(couponId),
			2,
			clock.instant().getEpochSecond() - 10,
			0,
			MOVE_SIZE
		);

		if (couponRequests == null || couponRequests.isEmpty()) {
			return;
		}
		// 방어 코드
		Set<ZSetOperations.TypedTuple<String>> popTuples = couponRequests.stream()
			.filter(tuple -> tuple.getValue() != null && tuple.getScore() != null)
			.collect(Collectors.toSet());

		if(popTuples.isEmpty()) return;

		redis.opsForZSet().add(CouponRedisKeys.popKey(couponId), popTuples);
	}

	private List<String> popUsers(Integer couponId) {
		// score >= 2 && score < 현재 초 - 10에 해당하는 요청 가져오기
		Set<ZSetOperations.TypedTuple<String>> tuples =
			redis.opsForZSet().popMin(CouponRedisKeys.popKey(couponId), POP_SIZE);
		if (tuples == null || tuples.isEmpty()) {
			return List.of();
		}

		return tuples.stream()
			.map(ZSetOperations.TypedTuple::getValue)
			// null 체크를 꼭 해줘야 하나?
			.filter(Objects::nonNull)
			.toList();
	}


}
