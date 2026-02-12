package kr.hhplus.be.server.infrastructure.persistence.redis;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import kr.hhplus.be.server.application.product.ProductSoldQtyDTO;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PopularRankRedisWriter {
	private final StringRedisTemplate redis;
	public void rebuildZsetWithSwap(String finalKey, List<ProductSoldQtyDTO> rows, Duration ttl) {
		if(rows == null || rows.isEmpty()) {
			return;
		}
		String tmpKey = finalKey + ":tmp";

		redis.delete(tmpKey);

		boolean wroteAny = false;
		var z = redis.opsForZSet();
		for (ProductSoldQtyDTO r : rows) {
			if (r.getProductId() == 0 || r.getSoldQty() == null) continue;
			if (r.getProductId() <= 0) continue;
			z.add(tmpKey, String.valueOf(r.getProductId()), r.getSoldQty().doubleValue());
			wroteAny = true;
		}
		if (!wroteAny) return;

		if (ttl != null) redis.expire(tmpKey, ttl);

		// finalKey가 있든 없든 "교체"됨
		redis.rename(tmpKey, finalKey);
	}
}
