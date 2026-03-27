package kr.hhplus.be.server.application.payment.dto;

import java.util.List;

import kr.hhplus.be.server.domain.payment.CancelType;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayType;
import lombok.Builder;
import lombok.Getter;

@Getter
public class CancelCommand {
	private Long paymentId;
	private Long orderId;
	private List<Long> targetOrderProducts;
	private Long cancelAmount;
	private String reason;
	private String idempotencyKey;
	private CancelType cancelType;
	private String pgTransactionId;
	private PaymentGatewayType gatewayType;

	@Builder
	public CancelCommand(Long paymentId, Long orderId, List<Long> targetOrderProducts, Long cancelAmount, String reason,
		String idempotencyKey, CancelType cancelType, String pgTransactionId, PaymentGatewayType gatewayType) {
		this.paymentId = paymentId;
		this.orderId = orderId;
		this.targetOrderProducts = targetOrderProducts;
		this.cancelAmount = cancelAmount;
		this.reason = reason;
		this.idempotencyKey = idempotencyKey;
		this.cancelType = cancelType;
		this.pgTransactionId = pgTransactionId;
		this.gatewayType = gatewayType;
	}
}
