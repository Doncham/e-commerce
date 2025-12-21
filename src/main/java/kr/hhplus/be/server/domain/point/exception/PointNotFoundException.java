package kr.hhplus.be.server.domain.point.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class PointNotFoundException extends PointException{
	public static String message = "user의 point를 찾을 수 없습니다. userId = ";

	public PointNotFoundException(ErrorCode code, Long userId) {
		super(code, message + userId);
	}
}
