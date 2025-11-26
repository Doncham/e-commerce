package kr.hhplus.be.server.order.request;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DraftCreateRequest {
	private Long cartId;
	private Long addressId;
	private String memo;
	@Builder
	public DraftCreateRequest(Long cartId, Long addressId, String memo) {
		this.cartId = cartId;
		this.addressId = addressId;
		this.memo = memo;
	}
}
