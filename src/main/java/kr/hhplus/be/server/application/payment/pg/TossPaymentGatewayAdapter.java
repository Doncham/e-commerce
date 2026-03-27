package kr.hhplus.be.server.application.payment.pg;

import org.springframework.stereotype.Component;

import kr.hhplus.be.server.api.payment.request.PaymentGatewayRequest;
import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TossPaymentGatewayAdapter implements PaymentGatewayAdapter{
	@Override
	public boolean supports(PaymentGatewayType gatewayType) {
		return gatewayType == PaymentGatewayType.TOSS;
	}
	@Override
	public PaymentGatewayResponse requestPayment(PaymentGatewayRequest request) {
		return null;
	}

	@Override
	public PaymentGatewayCancelResponse cancel(PaymentGatewayCancelRequest request) {
		return null;
	}


}
