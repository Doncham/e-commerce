package kr.hhplus.be.server.application.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import kr.hhplus.be.server.domain.usercoupon.exception.CouponIssueBusyException;
import kr.hhplus.be.server.exception.BusinessException;
import kr.hhplus.be.server.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
	// 쿠폰 획득 락 경합 실패 시 클라이언트에게 재시도 힌트 줌.
	@ExceptionHandler(CouponIssueBusyException.class)
	public ResponseEntity<ApiErrorResponse> handleBusy(CouponIssueBusyException e) {
		ErrorCode code = e.getCode();
		ApiErrorResponse body = ApiErrorResponse.of(code.name(), e.getMessage(), code.getStatus().value());

		return ResponseEntity.status(code.getStatus())
			// 재시도 힌트
			// 락 획득 실패 시 재시도를 클라이언트에 위임.
			.header("Retry-After", "0.2")
			.body(body);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleUnexpected(
		Exception exception,
		HttpServletRequest request
	) {
		log.error(
			"처리되지 않은 HTTP 예외. method={}, uri={}, thread={}",
			request.getMethod(),
			request.getRequestURI(),
			Thread.currentThread().getName(),
			exception
		);

		return ResponseEntity.internalServerError()
			.body(new ErrorResponse("INTERNAL_SERVER_ERROR", "서버 오류가 발생했습니다."));
	}

	public record ErrorResponse(String code, String message) {
	}

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ErrorResponse>
	handleBusinessException(
		BusinessException exception
	) {
		ErrorCode errorCode =
			exception.getErrorCode();

		log.warn(
			"Business error. code={}, detail={}",
			errorCode.getCode(),
			exception.getMessage()
		);

		return ResponseEntity
			.status(errorCode.getStatus())
			.body(
				new ErrorResponse(
					errorCode.getCode(),
					errorCode.getMessage()
				)
			);
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleValidationException(
		MethodArgumentNotValidException exception
	) {
		return ResponseEntity
			.badRequest()
			.body(
				new ErrorResponse(
					"COMMON_400",
					"요청 값이 올바르지 않습니다."
				)
			);
	}
}