package kr.hhplus.be.server.application.payment.pg;

import kr.hhplus.be.server.api.payment.request.PaymentGatewayRequest;
import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;

public interface PaymentGatewayPort {
	PaymentGatewayResponse requestPayment(PaymentGatewayRequest request);
	PaymentGatewayCancelResponse cancel(PaymentGatewayCancelRequest request);
}