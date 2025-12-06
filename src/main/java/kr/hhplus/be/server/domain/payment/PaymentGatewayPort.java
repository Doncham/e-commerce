package kr.hhplus.be.server.domain.payment;

import org.springframework.stereotype.Component;

import kr.hhplus.be.server.api.payment.request.PaymentGatewayRequest;
import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;

@Component
public interface PaymentGatewayPort {
	PaymentGatewayResponse requestPayment(PaymentGatewayRequest request);
}
