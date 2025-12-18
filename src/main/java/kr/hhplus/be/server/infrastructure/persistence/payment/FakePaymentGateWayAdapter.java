package kr.hhplus.be.server.infrastructure.persistence.payment;

import java.util.UUID;

import org.springframework.stereotype.Component;

import kr.hhplus.be.server.api.payment.request.PaymentGatewayRequest;
import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;
import kr.hhplus.be.server.domain.payment.PaymentGatewayPort;
import kr.hhplus.be.server.domain.payment.PaymentGatewayStatus;

@Component
public class FakePaymentGateWayAdapter implements PaymentGatewayPort {

	@Override
	public PaymentGatewayResponse requestPayment(PaymentGatewayRequest request) {
		String uuid = UUID.randomUUID().toString();
		String txNum = uuid.substring(0, 3);
		return PaymentGatewayResponse.of("tx-" + txNum,
			PaymentGatewayStatus.SUCCESS,
			request.getAmount());
	}
}
