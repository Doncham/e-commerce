package kr.hhplus.be.server.payment.port.out;

import org.springframework.stereotype.Component;

import kr.hhplus.be.server.payment.request.PaymentGatewayRequest;
import kr.hhplus.be.server.payment.response.PaymentGatewayResponse;

@Component
public interface PaymentGatewayPort {
	PaymentGatewayResponse requestPayment(PaymentGatewayRequest request);
}
