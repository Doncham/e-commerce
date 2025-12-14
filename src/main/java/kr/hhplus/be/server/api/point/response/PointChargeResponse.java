package kr.hhplus.be.server.api.point.response;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PointChargeResponse {
	private Long userId;
	private Long chargedAmount;
	private Long balanceAfterChange;
	private String idempotencyKey;

	@Builder
	public PointChargeResponse(Long userId, Long chargedAmount, Long balanceAfterChange, String idempotencyKey) {
		this.userId = userId;
		this.chargedAmount = chargedAmount;
		this.balanceAfterChange = balanceAfterChange;
		this.idempotencyKey = idempotencyKey;
	}
}
