package kr.hhplus.be.server.application.firstcomeCoupon.metric;

import java.util.EnumMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class CouponApplyMetrics {
	private static final String METRIC_NAME = "coupon.apply";
	private final Map<CouponApplyMetricResult, Counter> counters;

	public CouponApplyMetrics(MeterRegistry meterRegistry) {
		this.counters = new EnumMap<>(CouponApplyMetricResult.class);

		for (CouponApplyMetricResult result : CouponApplyMetricResult.values()) {
			Counter counter = Counter.builder(METRIC_NAME)
				.description("Redis coupon apply result count")
				.tag("result", result.name())
				.register(meterRegistry);

			counters.put(result, counter);
		}
	}

	public void increment(CouponApplyMetricResult result) {
		counters.get(result).increment();
	}
}
