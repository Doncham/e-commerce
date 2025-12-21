package kr.hhplus.be.server.application.payment;

import kr.hhplus.be.server.api.payment.request.PayRequest;
import kr.hhplus.be.server.api.payment.request.PayResponse;

public interface PayUseCase {
	PayResponse pay(PayRequest request);
}
