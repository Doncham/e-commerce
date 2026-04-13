package kr.hhplus.be.server.application.cache;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import kr.hhplus.be.server.api.product.response.PopularProductsResponse;

@Component
public class PopularLocalCache {
	private final Cache<String, PopularProductsResponse> cache = Caffeine.newBuilder()
		.expireAfterWrite(Duration.ofMinutes(1))
		.maximumSize(100)
		.build();

	public PopularProductsResponse get(String key) {
		return cache.getIfPresent(key);
	}

	public void put(String key, PopularProductsResponse value) {
		cache.put(key,value);
	}

	public void evict(String key) {
		cache.invalidate(key);
	}

}
