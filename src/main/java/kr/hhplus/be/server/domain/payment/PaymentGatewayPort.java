package kr.hhplus.be.server.domain.payment;


import kr.hhplus.be.server.api.payment.request.PaymentGatewayRequest;
import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;


public interface PaymentGatewayPort {
	PaymentGatewayResponse requestPayment(PaymentGatewayRequest request);
}
