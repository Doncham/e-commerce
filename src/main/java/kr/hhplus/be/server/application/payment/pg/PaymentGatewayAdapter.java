package kr.hhplus.be.server.application.payment.pg;

public interface PaymentGatewayAdapter extends PaymentGatewayPort {
	boolean supports(PaymentGatewayType gatewayType);
}
