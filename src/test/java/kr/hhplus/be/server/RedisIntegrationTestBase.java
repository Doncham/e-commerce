package kr.hhplus.be.server;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ActiveProfiles("test")
@Testcontainers
@SpringBootTest
public abstract class RedisIntegrationTestBase {

	@Container
	static GenericContainer<?> redis =
		new GenericContainer<>("redis:7-alpine")
			.withExposedPorts(6379);

	@DynamicPropertySource
	static void redisProps(DynamicPropertyRegistry registry) {
		registry.add("app.redisson.enabled", () -> true); // 이 테스트에서는 켠다

		// Spring Data Redis가 참조하는 값들 주입
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

	}
}