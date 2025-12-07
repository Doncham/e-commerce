package kr.hhplus.be.server.api.point.request;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PointChargeRequest {
	private long userId;
	private long amount;
	private String idempotencyKey;
	@Builder
	public PointChargeRequest(long userId, long amount, String idempotencyKey) {
		this.userId = userId;
		this.amount = amount;
		this.idempotencyKey = idempotencyKey;
	}
}
