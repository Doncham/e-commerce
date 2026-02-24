package kr.hhplus.be.server.application.firstcomeCoupon.worker;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import kr.hhplus.be.server.application.firstcomeCoupon.ActiveCouponRegistry;
import kr.hhplus.be.server.infrastructure.persistence.userCoupon.UserCouponRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class FirstComeCouponWorker {
	private static final Duration REQ_TTL = Duration.ofDays(2);
	// 한 번에 pop할 최대 개수 (DB/Redis 상황에 따라 50~200 설정)
	private static final int POP_BATCH_SIZE = 50;

	private final FirstComeCouponIssuer firstComeCouponIssuer;
	private final StringRedisTemplate redis;
	private final UserCouponRepository userCouponRepository;
	private final ActiveCouponRegistry activeCouponRegistry;


	/**
	 * 예시: 200ms마다 돌아서 빨리 비우는 형태(이벤트 트래픽에 따라 조절 가능)
	 */
	@Scheduled(fixedDelay = 200)
	public void run() {
		List<Long> couponIds = activeCouponRegistry.getActiveCouponIds();
		for (Long couponId : couponIds) {
			processCoupon(couponId, 200);
		}
	}

	public void processCoupon(long couponId, int maxBatch) {
		if(couponId <= 0 || maxBatch <=0 ) return;
		String reqKey = reqKey(couponId);
		String issuedKey = issuedKey(couponId);
		String remainKey = remainKey(couponId);

		int attempts = 0;
		while (attempts < maxBatch) {
			int want = Math.min(POP_BATCH_SIZE, maxBatch - attempts);

			// 1) 수량을 배치로 선점 (want 만큼)
			int reserved = reserveRemain(remainKey, want);
			if (reserved <= 0) {
				// 품절 또는 remainKey 이상
				return;
			}

			// 2) ZSET에서 reserved 만큼 원자 pop
			Set<ZSetOperations.TypedTuple<String>> popped = redis.opsForZSet().popMin(reqKey, reserved);
			if (popped == null || popped.isEmpty()) {
				// 큐가 비었는데 수량을 선점했으니 복구하고 종료
				releaseRemain(remainKey, reserved);
				return;
			}

			// pop된 개수가 reserved보다 적을 수 있음(경합/큐 부족)
			// ex) 50개 처리하는데 이벤트가 인기 없어서 30명만 신청한 경우 50-30만큼의 수량을 복구해줘야함.
			int actual = popped.size();
			if (actual < reserved) {
				releaseRemain(remainKey, reserved - actual);
			}

			// 3) pop된 요청 처리 (각 요청마다 DB 결과에 따라 remain 복구 가능)
			for (ZSetOperations.TypedTuple<String> t : popped) {
				String userIdStr = (t == null ? null : t.getValue());
				if (userIdStr == null || userIdStr.isBlank()) {
					// 이상 데이터: 발급 못 했으니 수량 복구
					releaseRemain(remainKey, 1);
					continue;
				}

				long userId;
				try {
					userId = Long.parseLong(userIdStr);
				} catch (Exception e) {
					releaseRemain(remainKey, 1);
					continue;
				}

				// 3-1) 워커 단계 중복 발급 방지
				Long issuedAdded = redis.opsForSet().add(issuedKey, userIdStr);
				// 키가 처음 만들어지는 순간에만 expire 명령어 날리기
				if (issuedAdded == 1L) {
					redis.expire(issuedKey, REQ_TTL);
				}
				if (issuedAdded == null) {
					// Redis 이상: 안정적으로 복구 후 종료
					releaseRemain(remainKey, 1);
					continue;
				}
				if (issuedAdded == 0L) {
					// 이미 처리됨: 수량 복구하고 다음
					releaseRemain(remainKey, 1);
					continue;
				}

				// 3-2) DB insert (최종 확정)
				IssueResult result = firstComeCouponIssuer.issueToDb(couponId, userId);
				Double score = t.getScore();

				if (result == IssueResult.SUCCESS) {
					attempts++;
					if(attempts >= maxBatch) break;
					continue;
				}

				// 실패면 보상작업: issued 제거 + remain 복구
				redis.opsForSet().remove(issuedKey, userIdStr);
				releaseRemain(remainKey, 1);

				if(result == IssueResult.PERMANENT_FAIL) continue;

				// 재시도 가능 시 requeue
				double requeueScore = score != null ? score : (double)System.currentTimeMillis();
				// backoff: 너무 빨리 다시 잡히지 않게 뒤로 넣기(5초)
				requeueScore += 5_000.0;

				redis.opsForZSet().add(reqKey, userIdStr, requeueScore);
				attempts++;

			}

		}
	}

	private int reserveRemain(String remainKey, int want) {
		// increment(key, delta)로 INCRBY 동작 (음수면 사실상 DECRBY)
		Long remainAfter = redis.opsForValue().increment(remainKey, -want);
		if (remainAfter == null) {
			log.warn("remain reserve returned null. remainKey={}", remainKey);
			return 0;
		}

		if (remainAfter >= 0) {
			return want;
		}

		// oversold: remainAfter가 -k면 k개 초과로 선점한 것 -> k개 복구
		int over = (int)(-remainAfter);
		if (over > 0) {
			redis.opsForValue().increment(remainKey, over);
		}
		int reserved = want - over;
		return Math.max(reserved, 0);
	}

	private void releaseRemain(String remainKey, int count) {
		if (count <= 0) return;
		redis.opsForValue().increment(remainKey, count);
	}




	private String reqKey(long couponId) {
		return "coupon:" + couponId + ":req";
	}

	private String issuedKey(long couponId) {
		return "coupon:" + couponId + ":issued";
	}

	private String remainKey(long couponId) {
		return "coupon:" + couponId + ":remain";
	}
}
