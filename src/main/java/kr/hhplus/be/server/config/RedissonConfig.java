package kr.hhplus.be.server.config;

import java.io.IOException;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

@Configuration
public class RedissonConfig {

	@Bean
	@ConditionalOnExpression(
		"'${app.redisson.enabled}' == 'true' and '${app.redisson.mode}' == 'file'"
	)
	public RedissonClient redissonClientFromFile(@Value("${app.redisson.file}") String file) throws
		IOException {
		String path = file.replace("classpath:", "");
		Config config = Config.fromYAML(new ClassPathResource(path).getInputStream());
		return Redisson.create(config);
	}
	// 테스트에서 실행되는 빈, testcontainer가 생성한 host,port를 RedissonClient가 사용
	@Bean
	@ConditionalOnExpression(
		"'${app.redisson.enabled}' == 'true' and '${app.redisson.mode}' == 'property'"
	)
	public RedissonClient redissonClientFromProps(
		@Value("${spring.data.redis.host}") String host,
		@Value("${spring.data.redis.port}") int port
	) {
		Config config = new Config();
		config.useSingleServer().setAddress("redis://" + host + ":" + port);
		return Redisson.create(config);
	}
}
