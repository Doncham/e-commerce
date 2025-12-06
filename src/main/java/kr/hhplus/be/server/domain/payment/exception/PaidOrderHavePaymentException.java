package kr.hhplus.be.server.domain.payment.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class PaidOrderHavePaymentException extends PaymentException {
	public static String Message = "이미 결제된 주문에는 결제 정보가 있어야합니다. orderId=";
	public PaidOrderHavePaymentException(ErrorCode code, String orderId) {
		super(code, Message + orderId);
	}
}
