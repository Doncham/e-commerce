package kr.hhplus.be.server.application.firstcomeCoupon;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ActiveCouponRegistry {
	private static final Duration ACTIVE_TTL = Duration.ofDays(2);

	private final StringRedisTemplate redis;

	public List<Long> getActiveCouponIds() {
		Set<String> members = redis.opsForSet().members(activeKey());
		if (members == null || members.isEmpty()) return List.of();

		List<Long> ids = new ArrayList<>(members.size());
		for (String s : members) {
			if (s == null || s.isBlank()) continue;
			try {
				long id = Long.parseLong(s);
				if (id > 0) ids.add(id);
			} catch (Exception ignore) {
			}
		}
		return ids;
	}

	// 이벤트 시작 시 등록
	public void addActiveCoupon(long couponId) {
		redis.opsForSet().add(activeKey(), String.valueOf(couponId));
		redis.expire(activeKey(), ACTIVE_TTL);
	}

	// 이벤트 종료 시 제거
	public void removeActiveCoupon(long couponId) {
		redis.opsForSet().remove(activeKey(), String.valueOf(couponId));
	}

	private String activeKey() {
		return "eventCoupon:active";
	}
}