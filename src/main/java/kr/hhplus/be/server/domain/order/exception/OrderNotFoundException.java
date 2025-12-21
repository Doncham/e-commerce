package kr.hhplus.be.server.domain.order.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class OrderNotFoundException extends OrderException {
	public static String Message = "주문을 찾을 수 없습니다. orderId=";
	public OrderNotFoundException(ErrorCode code, Long orderId) {
		super(code, Message + orderId);
	}
}
