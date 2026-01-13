package kr.hhplus.be.server.application.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import kr.hhplus.be.server.domain.usercoupon.exception.CouponIssueBusyException;
import kr.hhplus.be.server.exception.ErrorCode;

@RestControllerAdvice
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
}