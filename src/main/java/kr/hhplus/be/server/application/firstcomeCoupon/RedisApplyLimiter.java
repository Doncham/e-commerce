package kr.hhplus.be.server.application.firstcomeCoupon;

import java.util.concurrent.Semaphore;

import org.springframework.stereotype.Component;

@Component
// redis의 요청 큐잉으로 인한 병목 확인을 위한 limiter
public class RedisApplyLimiter {
	private final Semaphore semaphore = new Semaphore(100);

	public boolean tryAcquire() {
		return semaphore.tryAcquire();
	}

	public void release() {
		semaphore.release();
	}

	public int availablePermits() {
		return semaphore.availablePermits();
	}
}
