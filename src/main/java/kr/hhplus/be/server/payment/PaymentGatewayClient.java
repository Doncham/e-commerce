package kr.hhplus.be.server.payment;

import org.springframework.stereotype.Component;

import kr.hhplus.be.server.payment.request.PaymentGatewayRequest;
import kr.hhplus.be.server.payment.response.PaymentGatewayResponse;

@Component
public interface PaymentGatewayClient {
	PaymentGatewayResponse requestPayment(PaymentGatewayRequest request);
}
