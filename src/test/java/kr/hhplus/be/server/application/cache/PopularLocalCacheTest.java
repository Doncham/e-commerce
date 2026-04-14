package kr.hhplus.be.server.application.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import kr.hhplus.be.server.api.product.response.PopularProductsResponse;

class PopularLocalCacheTest {
	@Test
	void loadCountIncreaseJustOne() throws InterruptedException {
		int threadCount = 20;
		PopularLocalCache cache = new PopularLocalCache();
		AtomicInteger loadCount = new AtomicInteger();
		CountDownLatch ready = new CountDownLatch(threadCount);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threadCount);

		ExecutorService pool = Executors.newFixedThreadPool(threadCount);
		for (int i = 0; i < threadCount; i++) {
			pool.submit(() -> {
				ready.countDown();
				try {
					start.await();
					cache.getOrLoad("key", k -> {
						loadCount.incrementAndGet();
						try {
							Thread.sleep(300);
						} catch (InterruptedException e) {
							throw new RuntimeException(e);
						}
						return new PopularProductsResponse();
					});
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				} finally {
					done.countDown();
				}
			});
		}

		ready.await();
		start.countDown();
		done.await();
		Assertions.assertEquals(1, loadCount.get());
		pool.shutdown();
	}
}