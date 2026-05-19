package kr.hhplus.be.server.application.firstcomeCoupon;

public class ApplyResponseDto {
	private final boolean success;
	private final ApplyCode code;
	private final String message;

	private ApplyResponseDto(boolean success, ApplyCode code, String message) {
		this.success = success;
		this.code = code;
		this.message = message;
	}

	public static ApplyResponseDto ok(ApplyCode code, String message) {
		return new ApplyResponseDto(true, code, message);
	}

	public static ApplyResponseDto fail(ApplyCode code, String message) {
		return new ApplyResponseDto(false, code, message);
	}

	public boolean isSuccess() { return success; }
	public ApplyCode getCode() { return code; }
	public String getMessage() { return message; }
	// 와 이거 이렇게 같이 놓을 수가 있네
	public enum ApplyCode { ACCEPTED, DUPLICATE, ERROR }
}
