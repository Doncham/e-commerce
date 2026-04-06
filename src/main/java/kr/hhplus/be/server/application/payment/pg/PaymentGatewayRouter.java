package kr.hhplus.be.server.application.payment.pg;

import java.util.List;

import org.springframework.stereotype.Component;

import kr.hhplus.be.server.domain.payment.PaymentGatewayPort;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentGatewayRouter {
	private final List<PaymentGatewayAdapter> adapters;

	public PaymentGatewayPort route(PaymentGatewayType gatewayType) {
		return adapters.stream()
			.filter(adapter -> adapter.supports(gatewayType))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("No adapter"));
	}
}
