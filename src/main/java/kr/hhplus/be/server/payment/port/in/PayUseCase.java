package kr.hhplus.be.server.payment.port.in;

import kr.hhplus.be.server.payment.request.PayResponse;

public interface PayUseCase {
	PayResponse pay(Long orderId);
}
