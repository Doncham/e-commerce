package kr.hhplus.be.server.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.getMessage());
		this.errorCode = errorCode;
	}

	/**
	 * detail은 서버 로그용이다.
	 * 클라이언트에는 ErrorCode.message만 반환한다.
	 */
	public BusinessException(
		ErrorCode errorCode,
		String detailMessage
	) {
		super(detailMessage);
		this.errorCode = errorCode;
	}

	public static BusinessException of(
		ErrorCode errorCode
	) {
		return new BusinessException(errorCode);
	}

	public static BusinessException of(
		ErrorCode errorCode,
		String detailMessage
	) {
		return new BusinessException(
			errorCode,
			detailMessage
		);
	}
}