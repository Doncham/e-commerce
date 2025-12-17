package kr.hhplus.be.server.domain.order.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class OrderAlreadyPaidOrderException extends OrderException{
	private static final String message = "주문이 이미 결제되었습니다. orderId = ";
	public OrderAlreadyPaidOrderException(ErrorCode code, Long orderId) {
		super(code, message + orderId);
	}
}
