package kr.hhplus.be.server.application.firstcomeCoupon.metric;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public class MeteredCallerRunsPolicy implements RejectedExecutionHandler {
	private static final String METRIC_NAME = "coupon.issue.executor.caller.runs";
	private final Counter callerRunsCounter;

	public MeteredCallerRunsPolicy(
		MeterRegistry meterRegistry,
		String executorName ) {
		this.callerRunsCounter = Counter.builder(METRIC_NAME)
			.description("Number of coupon issue tasks executed by the caller thread" )
			.tag("name", executorName)
			.register(meterRegistry);
	}
	@Override
	public void rejectedExecution(Runnable task, ThreadPoolExecutor executor) {
		// CallerRunsPolicy와 동일하게 종료된 Executor에서는 실행하지 않는다.
		if (executor.isShutdown()) { return; }

		callerRunsCounter.increment();

		// 작업을 제출한 스레드가 직접 실행한다.
		task.run();
	}
}
