package kr.hhplus.be.server.domain.payment.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class PayAmountMisMatchException extends PaymentException {
	public static String Message = "요청 결제 금액과 PG 응답 금액이 일치하지 않습니다. orderId=";
	public PayAmountMisMatchException(ErrorCode code, String paymentId) {
		super(code, Message + paymentId);
	}
}
