package kr.hhplus.be.server.api.exception;

public class InvalidInternalTokenException extends RuntimeException{
	public InvalidInternalTokenException(String message) {
		super(message);
	}
}
