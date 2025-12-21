package kr.hhplus.be.server.domain.point.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class PointUseNegativeException extends PointException{
	private static String messgae = "음수 포인트는 사용할 수 없습니다. orderId= ";
	public PointUseNegativeException(ErrorCode code, Long orderId) {
		super(code, messgae + orderId);
	}
}
