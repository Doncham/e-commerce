package kr.hhplus.be.server.application.payment.pg;

import kr.hhplus.be.server.domain.payment.PaymentGatewayPort;

public interface PaymentGatewayAdapter extends PaymentGatewayPort {
	boolean supports(PaymentGatewayType gatewayType);
}
