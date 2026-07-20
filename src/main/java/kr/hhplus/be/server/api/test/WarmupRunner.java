package kr.hhplus.be.server.api.test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class WarmupRunner implements ApplicationRunner {

	private final RestClient.Builder restClientBuilder;

	@Override
	public void run(ApplicationArguments args) throws Exception {
		if (!args.containsOption("warmup")) {
			return;
		}

		int count = getCount(args);

		RestClient client = restClientBuilder
			.baseUrl("http://localhost:8080")
			.build();

		log.info("[HttpWarmupRunner] started. count={}", count);

		// 내부에서 4개 스레드가 동시에 HTTP 요청을 보낸다.
		runConcurrentWarmup(client, count);

		client.delete()
			.uri("/api/warmup/deleteKey")
			.retrieve()
			.toBodilessEntity();

		log.info("[HttpWarmupRunner] warmup finished");
	}

	private void runConcurrentWarmup(RestClient client, int totalCount) throws Exception {
		// 동시에 warm-up 요청을 보낼 스레드 수
		int concurrency = 4;


		 // 각 스레드가 요청 후 쉬는 시간
		 // 40ms마다 요청하면 스레드 1개당 약 25RPS
		 // 스레드 4개면 전체 약 100RPS
		int sleepMillis = 40;

		// 스레드마다 요청 수: 750개
		int perThreadCount = totalCount / concurrency;

		ExecutorService executor = Executors.newFixedThreadPool(concurrency);
		// 모든 warm-up이 끝날 때까지 대기시키기
		List<Future<?>> futures = new ArrayList<>();

		try {
			for (int threadIndex = 0; threadIndex < concurrency; threadIndex++) {
				futures.add(executor.submit(() -> {
					for (int i = 0; i < perThreadCount; i++) {

						client.get()
							.uri("/api/warmup/full")
							.retrieve()
							.toBodilessEntity();

						try {
							// 전체 RPS를 너무 높이지 않기 위해 잠깐 쉰다.
							Thread.sleep(sleepMillis);
						} catch (InterruptedException e) {
							throw new RuntimeException(e);
						}
					}
				}));
			}


			// 모든 warm-up worker가 끝날 때까지 대기

			for (Future<?> future : futures) {
				future.get();
			}
		} finally {
			/*
			 * 스레드풀 종료
			 */
			executor.shutdown();
		}
	}

	private int getCount(ApplicationArguments args) {
		List<String> values = args.getOptionValues("warmup-count");

		if (values == null || values.isEmpty()) {
			return 100;
		}

		return Integer.parseInt(values.get(0));
	}
}