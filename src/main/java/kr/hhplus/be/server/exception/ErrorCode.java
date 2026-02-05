package kr.hhplus.be.server.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// 400 Bad Request (요청 값/상태가 잘못됨)
	NEGATIVE_CHARGE(HttpStatus.BAD_REQUEST, "음수 포인트 충전 시도"),
	NEGATIVE_POINT_USE(HttpStatus.BAD_REQUEST, "음수 포인트 사용"),
	POINT_AMOUNT_NOT_VALID(HttpStatus.BAD_REQUEST, "포인트 금액이 유효하지 않음"),
	PAY_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "결제 금액 불일치"),
	PAID_ORDER_MUST_HAVE_PAYMENT(HttpStatus.BAD_REQUEST, "결제된 주문에 결제 정보가 없음"),

	// 404 Not Found
	NOT_FOUND_INVENTORY(HttpStatus.NOT_FOUND, "인벤토리 없음"),
	NOT_FOUND_ORDER(HttpStatus.NOT_FOUND, "주문 없음"),
	NOT_FOUND_COUPON(HttpStatus.NOT_FOUND, "쿠폰 없음"),
	NOT_FOUND_POINT(HttpStatus.NOT_FOUND, "포인트 없음"),
	NOT_FOUND_POINT_CHARGE(HttpStatus.NOT_FOUND, "포인트 충전 없음"),
	NOT_FOUND_POINT_RESERVATION(HttpStatus.NOT_FOUND, "포인트 예약 없음"),
	NOT_FOUNT_PAYMENT(HttpStatus.NOT_FOUND, "결제 정보 없음"),

	// 409 Conflict (리소스 상태 충돌 / 중복 / 재고/한도 문제)
	NOT_ENOUGH_POINT(HttpStatus.CONFLICT, "포인트 부족"),
	NOT_SUFFICIENT_STOCK(HttpStatus.CONFLICT, "재고 부족"),
	EXPIRED_COUPON(HttpStatus.CONFLICT, "쿠폰 만료"),
	COUPON_FOR_USER_LIMIT_EXCEED(HttpStatus.CONFLICT, "유저 쿠폰 발급 한도 초과"),
	COUPON_ISSUE_LIMIT_EXCEED(HttpStatus.CONFLICT, "쿠폰 발급 한도 초과"),
	ALREADY_PAID_PAYMENT(HttpStatus.CONFLICT, "이미 결제됨(결제)"),
	ALREADY_PAID_ORDER(HttpStatus.CONFLICT, "이미 결제됨(주문)"),

	// 429 Too Many Request (락 획득 실패)
	COUPON_ISSUE_BUSY(HttpStatus.TOO_MANY_REQUESTS, "쿠폰 발급락 획득 실패");



	private final HttpStatus status;
	private final String defaultMessage;
}
