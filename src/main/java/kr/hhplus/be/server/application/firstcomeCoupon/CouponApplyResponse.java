package kr.hhplus.be.server.application.firstcomeCoupon;

public class CouponApplyResponse {
	private final boolean success;
	private final CouponApplyStatus status;
	private final String message;

	private CouponApplyResponse(boolean success, CouponApplyStatus status, String message) {
		this.success = success;
		this.status = status;
		this.message = message;
	}

	public static CouponApplyResponse ok(CouponApplyStatus code, String message) {
		return new CouponApplyResponse(true, code, message);
	}

	public static CouponApplyResponse fail(CouponApplyStatus code, String message) {
		return new CouponApplyResponse(false, code, message);
	}

	public boolean isSuccess() { return success; }
	public CouponApplyStatus getStatus() { return status; }
	public String getMessage() { return message; }
	// 와 이거 이렇게 같이 놓을 수가 있네
	public enum CouponApplyStatus { ACCEPTED, DUPLICATE, SOLD_OUT, ERROR }
}
