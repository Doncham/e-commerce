package kr.hhplus.be.server.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TimeConfig {
	@Bean
	public Clock clock() {
		return Clock.systemUTC(); // 또는 system(ZoneId.of("Asia/Seoul"))
	}
}