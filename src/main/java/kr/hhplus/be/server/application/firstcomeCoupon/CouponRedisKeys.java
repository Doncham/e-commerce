package kr.hhplus.be.server.application.firstcomeCoupon;

public class CouponRedisKeys {
	// 생성 막기, 다 static 이잖아
	private CouponRedisKeys() {}
	public static String reqKey(long couponId) {
		return "coupon:" + couponId + ":req";
	}

	public static String popKey(long couponId) {
		return "coupon:" + couponId + ":pop";
	}

	public static String quantityKey(long couponId) {
		return "coupon:" + couponId + ":quantity";
	}

	public static String eventCouponKey() {
		return "coupon:event:keys";
	}

	public static String lockKey(long couponId) {
		return "lock:coupon:" + couponId;
	}
}
