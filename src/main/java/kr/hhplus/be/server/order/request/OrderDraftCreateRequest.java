package kr.hhplus.be.server.order.request;

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
	@Builder
	public OrderDraftCreateRequest(Long cartId, Long addressId, String memo, Long userId, Long couponId) {
		this.cartId = cartId;
		this.addressId = addressId;
		this.memo = memo;
		this.userId = userId;
		this.couponId = couponId;
	}
}
