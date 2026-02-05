package kr.hhplus.be.server.application.exception;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {
	private final String code;
	private final String message;
	private final Integer status;
	private final LocalDateTime timestamp;

	private ApiErrorResponse(String code, String message, Integer status, LocalDateTime timestamp) {
		this.code = code;
		this.message = message;
		this.status = status;
		this.timestamp = timestamp;
	}

	public static ApiErrorResponse of(String code, String message) {
		return new ApiErrorResponse(code, message, null, LocalDateTime.now());
	}

	public static ApiErrorResponse of(String code, String message, int status) {
		return new ApiErrorResponse(code, message, status, LocalDateTime.now());
	}

	public String getCode() { return code; }
	public String getMessage() { return message; }
	public Integer getStatus() { return status; }
	public LocalDateTime getTimestamp() { return timestamp; }
}
