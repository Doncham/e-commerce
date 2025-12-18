package kr.hhplus.be.server.domain.pointReservation.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class PointReservationException extends RuntimeException{
	private final ErrorCode code;
	public PointReservationException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}
}
