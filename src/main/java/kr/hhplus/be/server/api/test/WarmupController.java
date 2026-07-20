package kr.hhplus.be.server.api.test;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import kr.hhplus.be.server.infrastructure.persistence.coupon.CouponRepository;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WarmupController {
	private final WarmupService warmupService;

	@GetMapping("/internal/warmup/hold")
	public ResponseEntity<Void> hold(
		@RequestParam(defaultValue = "2000") long millis
	) throws InterruptedException {
		Thread.sleep(millis);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/warmup/ping")
	public String ping() {
		return "ok";
	}

	@GetMapping("/json")
	public WarmupResponse json() {
		return new WarmupResponse("ok", System.currentTimeMillis());
	}
	public record WarmupResponse(String status, long timestamp) {
	}

	@GetMapping("/warmup/full")
	public String full() {
		warmupService.warmupFull();
		return "ok";
	}

	@DeleteMapping("/warmup/deleteKey")
	public String deleteZset() {
		warmupService.deleteKey();
		return "ok";
	}





}
