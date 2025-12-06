package kr.hhplus.be.server.application.payment;

import kr.hhplus.be.server.api.payment.request.PayResponse;

public interface PayUseCase {
	PayResponse pay(Long orderId);
}
