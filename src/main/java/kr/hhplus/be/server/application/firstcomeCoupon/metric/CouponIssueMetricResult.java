package kr.hhplus.be.server.application.firstcomeCoupon.metric;

public enum CouponIssueMetricResult {
	SUCCESS,
	DUPLICATE,
	DB_ERROR,
	REDIS_MARK_ERROR
}
