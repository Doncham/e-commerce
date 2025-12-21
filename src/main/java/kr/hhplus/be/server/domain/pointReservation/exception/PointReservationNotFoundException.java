package kr.hhplus.be.server.domain.pointReservation.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class PointReservationNotFoundException extends PointReservationException{
	private static final String message = "pointReservation을 찾을 수 없습니다. orderId = ";
	public PointReservationNotFoundException(ErrorCode code, Long id) {
		super(code, message + id);
	}
}
