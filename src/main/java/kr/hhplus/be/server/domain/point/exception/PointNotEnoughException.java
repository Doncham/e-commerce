package kr.hhplus.be.server.domain.point.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class PointNotEnoughException extends PointException{
	private static String message = "point의 잔액이 부족합니다. pointId = ";
	public PointNotEnoughException(ErrorCode code, Long pointId) {
		super(code, message + pointId);
	}
}
