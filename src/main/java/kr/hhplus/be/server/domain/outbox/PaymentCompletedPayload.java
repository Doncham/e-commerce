package kr.hhplus.be.server.domain.outbox;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentCompletedPayload {
	private Long userId;
	private Long orderId;
	private Long payAmount;
	private String pgTransactionId;

	private PaymentCompletedPayload(Long userId, Long orderId, Long payAmount, String pgTransactionId) {
		this.userId = userId;
		this.orderId = orderId;
		this.payAmount = payAmount;
		this.pgTransactionId = pgTransactionId;
	}

	public static PaymentCompletedPayload of(Long userId, Long orderId, Long payAmount, String pgTransactionId) {
		return new PaymentCompletedPayload(userId, orderId, payAmount, pgTransactionId);
	}

}
