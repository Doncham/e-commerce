package kr.hhplus.be.server.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

	// 400 Bad Request (요청 값/상태가 잘못됨)
	NEGATIVE_CHARGE(HttpStatus.BAD_REQUEST, "POINT_001","음수 포인트 충전 시도"),
	NEGATIVE_POINT_USE(HttpStatus.BAD_REQUEST, "POINT_002","음수 포인트 사용"),
	POINT_AMOUNT_NOT_VALID(HttpStatus.BAD_REQUEST,"POINT_003", "포인트 금액이 유효하지 않음"),
	PAY_AMOUNT_MISMATCH(HttpStatus.BAD_REQUEST, "PAYMENT_001","결제 금액 불일치"),
	PAID_ORDER_MUST_HAVE_PAYMENT(HttpStatus.BAD_REQUEST, "PAYMENT_002","결제된 주문에 결제 정보가 없음"),
	PAYMENT_GATEWAY_REJECTED(HttpStatus.REQUEST_TIMEOUT, "PG_001", "PG가 요청을 거절했습니다."),

	// 404 Not Found
	INVENTORY_NOT_FOUND(HttpStatus.NOT_FOUND, "INVENTORY_001","인벤토리 없음"),
	ORDER_NOT_FOUND(HttpStatus.NOT_FOUND,"ORDER_001", "주문 없음"),
	COUPON_NOT_FOUND(HttpStatus.NOT_FOUND,"COUPON_001", "쿠폰 없음"),
	POINT_NOT_FOUND(HttpStatus.NOT_FOUND, "POINT_004","포인트 없음"),
	POINT_CHARGE_NOT_FOUND(HttpStatus.NOT_FOUND,"POINT_005", "포인트 충전 없음"),
	POINT_RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND,"POINT_006", "포인트 예약 없음"),
	PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PAYMENT_003","결제 정보 없음"),
	ADDRESS_NOT_FOUND(HttpStatus.NOT_FOUND, "ADDRESS_001", "사용 가능한 배송지를 찾을 수 없습니다."),
	INVALID_ORDER_ITEM(HttpStatus.BAD_REQUEST, "ORDER_003", "주문할 수 없는 장바구니 상품이 포함되어 있습니다."),
	DUPLICATE_ORDER_ITEM(HttpStatus.BAD_REQUEST, "ORDER_004", "중복된 장바구니 상품이 포함되어 있습니다."),
	ORDER_QUANTITY_EXCEEDS_CART(HttpStatus.BAD_REQUEST, "ORDER_005", "주문 수량이 장바구니 수량을 초과했습니다."),
	USER_NOT_FOUND(HttpStatus.NOT_FOUND, "USER_001", "사용자를 찾을 수 없습니다."),

	// 409 Conflict (리소스 상태 충돌 / 중복 / 재고/한도 문제)
	POINT_NOT_ENOUGH(HttpStatus.CONFLICT,"POINT_007", "포인트 부족"),
	STOCK_NOT_SUFFICIENT(HttpStatus.CONFLICT, "INVENTORY_002","상품 재고 부족"),
	COUPON_EXPIRED(HttpStatus.CONFLICT, "COUPON_002","쿠폰 만료"),
	COUPON_FOR_USER_LIMIT_EXCEED(HttpStatus.CONFLICT, "COUPON_003","유저 쿠폰 발급 한도 초과"),
	COUPON_ISSUE_LIMIT_EXCEED(HttpStatus.CONFLICT, "COUPON_004","쿠폰 발급 한도 초과"),
	ALREADY_PAID_PAYMENT(HttpStatus.CONFLICT, "PAYMENT_004","이미 결제됨(결제)"),
	ALREADY_PAID_ORDER(HttpStatus.CONFLICT, "ORDER_002","이미 결제됨(주문)"),
	ORDER_NOT_MODIFIABLE(HttpStatus.CONFLICT, "ORDER_001", "현재 상태에서는 주문을 변경할 수 없습니다."),
	PAYMENT_CONFIRMING(HttpStatus.CONFLICT, "PAYMENT_005", "결제 승인 처리가 진행 중입니다."),
	PRODUCT_NOT_ORDERABLE(HttpStatus.CONFLICT, "PRODUCT_001", "현재 주문할 수 없는 상품입니다."),

	// 429 Too Many Request (락 획득 실패)
	COUPON_ISSUE_BUSY(HttpStatus.TOO_MANY_REQUESTS, "COUPON_004","쿠폰 발급락 획득 실패"),

	// 503 Service Unavailable, 외부 결제 시스템 일시 장애
	PAYMENT_CANCEL_FAILED(HttpStatus.SERVICE_UNAVAILABLE, "PG_002","PG payment cancel 실패"),
	PAYMENT_GATEWAY_TEMPORARILY_STOP(HttpStatus.SERVICE_UNAVAILABLE, "PG_003","PG 서버에 잠깐 오류 발생");



	private final HttpStatus status;
	private final String code;
	private final String message;
}
