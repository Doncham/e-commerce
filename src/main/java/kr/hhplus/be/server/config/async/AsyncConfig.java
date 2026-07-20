package kr.hhplus.be.server.config.async;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import io.micrometer.core.instrument.MeterRegistry;
import kr.hhplus.be.server.application.firstcomeCoupon.metric.MeteredCallerRunsPolicy;
import lombok.extern.slf4j.Slf4j;

@Configuration
@EnableAsync
@Slf4j
public class AsyncConfig implements AsyncConfigurer {
	@Value("${QUEUE_SIZE:100}")
	private Integer queueSize;
	@Value("${ASYNC_THREAD_SIZE:8}")
	private Integer thread_size;
	@Value("${ASYNC_THREAD_MAX_SIZE:16}")
	private Integer thread_max_size;
	@Value("${ASYNC_THREAD_PRE_START:true}")
	private Boolean preStart;

	@Bean(name = "couponIssueExecutor")
	public ThreadPoolTaskExecutor couponIssueExecutor(MeterRegistry meterRegistry) {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

		executor.setCorePoolSize(thread_size);
		executor.setMaxPoolSize(thread_max_size);
		executor.setQueueCapacity(queueSize);
		executor.setThreadNamePrefix("coupon-issue-");
		executor.setPrestartAllCoreThreads(preStart);
		executor.setRejectedExecutionHandler(new MeteredCallerRunsPolicy(
			meterRegistry,
			"couponIssueExecutor"
		));

		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(30);

		executor.initialize();
		return executor;
	}

	@Override
	public AsyncUncaughtExceptionHandler
	getAsyncUncaughtExceptionHandler() {

		return (
			Throwable exception,
			Method method,
			Object... parameters
		) -> log.error(
			"처리되지 않은 비동기 예외. class={}, method={}, params={}, thread={}",
			method.getDeclaringClass().getSimpleName(),
			method.getName(),
			Arrays.toString(parameters),
			Thread.currentThread().getName(),
			exception
		);
	}
}
