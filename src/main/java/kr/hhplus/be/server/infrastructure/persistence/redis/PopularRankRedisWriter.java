package kr.hhplus.be.server.infrastructure.persistence.redis;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import kr.hhplus.be.server.application.product.ProductSoldQtyDTO;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PopularRankRedisWriter {
	private final StringRedisTemplate redis;
	public void rebuildZsetWithSwap(String finalKey, List<ProductSoldQtyDTO> rows, Duration ttl) {
		if(rows.isEmpty()) {
			return;
		}
		String tmpKey = finalKey + ":tmp";
		String oldKey = finalKey + ":old";

		// 1) 임시 키 삭제(이전 잔재 제거)
		redis.delete(tmpKey);
		redis.delete(oldKey);

		// 2) 임시 키에 ZADD(배치)
		ZSetOperations<String, String> z = redis.opsForZSet();
		for (ProductSoldQtyDTO r : rows) {
			if (r.getProductId() == 0 || r.getSoldQty() == null) continue;
			// score는 double이지만 soldQty는 정수(Long)라 안전
			z.add(tmpKey, String.valueOf(r.getProductId()), r.getSoldQty().doubleValue());
		}

		// 3) TTL 설정(임시 키에도 걸어둠)
		if (ttl != null) {
			redis.expire(tmpKey, ttl);
		}

		// 4) rename:old 조회 로직에서 finalKey값이 없으면 old 조회하도록해서 데이터 조회를 못하는 순간을 없앤다.
		Boolean hasFinal = redis.hasKey(finalKey);
		if (Boolean.TRUE.equals(hasFinal)) {
			// rename은 해당키가 없으면 예외 터짐.
			redis.rename(finalKey, oldKey);
		}
		// 결제가 하나도 안된 상황이면 tmpKey가 존재하지 않음.

		redis.rename(tmpKey, finalKey);
		redis.delete(oldKey);
	}
}
