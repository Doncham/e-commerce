package kr.hhplus.be.server.application.firstcomeCoupon.metric;

public enum CouponApplyMetricResult {
	ACCEPTED,
	DUPLICATE,
	SOLD_OUT,
	QUANTITY_NOT_INITIALIZED,
	REDIS_ERROR,
	NULL_RESULT,
	UNKNOWN_RESULT
}
