package kr.hhplus.be.server.application.firstcomeCoupon.metric;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class CouponIssueMetrics {

	private static final String METRIC_NAME = "coupon.issue";

	private final Map<CouponIssueMetricResult, Counter> counters;

	public CouponIssueMetrics(MeterRegistry meterRegistry) {
		this.counters =
			new EnumMap<>(CouponIssueMetricResult.class);

		for (CouponIssueMetricResult result
			: CouponIssueMetricResult.values()) {

			Counter counter = Counter.builder(METRIC_NAME)
				.description(
					"Coupon asynchronous issue result count"
				)
				.tag("result", result.name())
				.register(meterRegistry);

			counters.put(result, counter);
		}
	}

	public void increment(CouponIssueMetricResult result) {
		Counter counter = counters.get(result);

		if (counter == null) {
			throw new IllegalArgumentException(
				"Unsupported coupon issue metric result: "
					+ result
			);
		}

		counter.increment();
	}
}
