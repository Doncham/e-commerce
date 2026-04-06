package kr.hhplus.be.server.domain.payment;


import kr.hhplus.be.server.api.payment.request.PaymentGatewayRequest;
import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayCancelRequest;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayCancelResponse;

public interface PaymentGatewayPort {
	PaymentGatewayResponse requestPayment(PaymentGatewayRequest request);
	PaymentGatewayCancelResponse cancel(PaymentGatewayCancelRequest request);
}
