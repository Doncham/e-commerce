package kr.hhplus.be.server.api.point.response;

import kr.hhplus.be.server.domain.pointcharge.ChargeStatus;
import kr.hhplus.be.server.domain.pointcharge.PointCharge;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PointChargeResponse {
	private Long userId;
	private Long chargedAmount;
	private Long balanceAfterChange;
	private String idempotencyKey;
	private ChargeStatus status;
	private String failReason;

	private PointChargeResponse(Long userId, Long chargedAmount, Long balanceAfterChange, String idempotencyKey, ChargeStatus status, String failReason) {
		this.userId = userId;
		this.chargedAmount = chargedAmount;
		this.balanceAfterChange = balanceAfterChange;
		this.idempotencyKey = idempotencyKey;
		this.status = status;
		this.failReason = failReason == null ? "" : failReason;
	}

	public static PointChargeResponse of(PointCharge pc) {
		return new PointChargeResponse(pc.getUserId(),
			pc.getAmount(),
			pc.getBalanceAfterChange(),
			pc.getIdempotencyKey(),
			pc.getStatus(),
			pc.getFailReason()
		);
	}
}
