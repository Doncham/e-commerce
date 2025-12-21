package kr.hhplus.be.server.api.order.request;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OrderDraftCreateRequest {
	private Long cartId;
	private Long addressId;
	private String memo;
	// 임시
	private Long userId;
	private Long couponId;
	private Long pointUseAmount;
	private String idempotencyKey;
	@Builder
	public OrderDraftCreateRequest(Long cartId, Long addressId, String memo, Long userId, Long couponId, Long pointUseAmount, String idempotencyKey) {
		this.cartId = cartId;
		this.addressId = addressId;
		this.memo = memo;
		this.userId = userId;
		this.couponId = couponId;
		this.pointUseAmount = pointUseAmount;
		this.idempotencyKey = idempotencyKey;
	}
}
