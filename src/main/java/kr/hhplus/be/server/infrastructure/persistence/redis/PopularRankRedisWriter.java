package kr.hhplus.be.server.infrastructure.persistence.redis;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.application.product.PopularScoreCodec;
import kr.hhplus.be.server.application.product.ProductSnap;
import kr.hhplus.be.server.application.product.ProductSoldQtyDTO;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PopularRankRedisWriter {
	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;
	public void rebuildZsetWithSwap(String finalKey, List<ProductSoldQtyDTO> rows, Duration ttl) {
		if(rows == null || rows.isEmpty()) {
			return;
		}
		String tmpKey = finalKey + ":tmp";
		String oldKey = finalKey + ":old";

		redis.delete(tmpKey);

		boolean wroteAny = false;
		var z = redis.opsForZSet();
		for (ProductSoldQtyDTO r : rows) {
			long productId = r.getProductId();
			Long qty = r.getSoldQty();
			if (productId == 0 || qty == null) continue;
			if (qty <= 0) continue;
			double score = PopularScoreCodec.encode(qty, productId);
			z.add(tmpKey, String.valueOf(r.getProductId()), score);
			wroteAny = true;
		}
		if (!wroteAny || Boolean.FALSE.equals(redis.hasKey(tmpKey))) return;

		if (ttl != null) redis.expire(tmpKey, ttl);

		if (Boolean.TRUE.equals(redis.hasKey(finalKey))) {
			redis.delete(oldKey);
			redis.rename(finalKey, oldKey);
			// oldKey는 잠깐만 쓰고 삭제할거라 TTL 짧게
			redis.expire(oldKey, Duration.ofMinutes(2));
		}

		// final은 이미 old로 빠짐.
		redis.rename(tmpKey, finalKey);
	}

	public void writeProductSnapshots(List<ProductSnap> snaps, Duration ttl) {
		if(snaps == null || snaps.isEmpty()) return;

		redis.executePipelined((RedisCallback<Object>) connection -> {
			RedisSerializer<String> serializer = redis.getStringSerializer();

			for (ProductSnap s : snaps) {
				if(s.getProductId() == null || s.getProductId() <= 0) continue;

				String key = snapKey(s.getProductId());
				try {
					String json = objectMapper.writeValueAsString(s);

					byte[] k = serializer.serialize(key);
					byte[] v = serializer.serialize(json);

					connection.stringCommands().set(k, v);
					if(ttl != null)
						connection.keyCommands().expire(k, ttl.getSeconds());
				} catch (Exception ignore) {
					// 배치는 일부 실패해도 다음 조회에서 복구 가능.
				}
			}
			return null;
		});

	}

	private String snapKey(Long productId) {
		return "product:snap:" + productId;
	}
}
