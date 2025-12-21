package kr.hhplus.be.server.domain.inventory.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class InventoryException extends RuntimeException {
	private final ErrorCode code;

	public InventoryException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}
}
