package kr.hhplus.be.server.point.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class PointAmountNotValidException extends PointException {
	private static String Message = "포인트 금액이 유효하지 않습니다. orderId=";
	public PointAmountNotValidException(ErrorCode errorCode, Long orderId) {
		super(errorCode, Message + orderId);
	}
}
