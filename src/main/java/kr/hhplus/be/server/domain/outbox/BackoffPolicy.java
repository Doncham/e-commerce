package kr.hhplus.be.server.domain.outbox;

import java.time.LocalDateTime;

public class BackoffPolicy {
	public static LocalDateTime nextRetryAt(int retryCount) {
		long baseMs = 1000L; // 1초
		long maxMs = 5 * 60 * 1000L; // 5분 cap
		long delay = Math.min(maxMs, baseMs * (1L << Math.min(retryCount, 10))); // 2^n

		// jitter: 0.5x~1.5x
		double jitter = 0.5 + Math.random();
		long jittered = (long) (delay * jitter);

		return LocalDateTime.now().plusNanos(jittered * 1_000_000);
	}
}
