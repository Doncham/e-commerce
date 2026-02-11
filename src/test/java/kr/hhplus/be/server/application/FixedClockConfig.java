package kr.hhplus.be.server.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class FixedClockConfig {
	@Primary
	@Bean
	public Clock fixedClock() {
		ZoneId KST = ZoneId.of("Asia/Seoul");
		Instant instant = LocalDateTime.of(2026, 2, 11, 0, 0, 0)
			.atZone(KST)
			.toInstant();

		return Clock.fixed(instant, KST);
	}
}
