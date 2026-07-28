package kr.hhplus.be.server.api.order.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OrderDraftItemRequest {
	@NotNull
	private Long cartItemId;
	@Positive
	private Long orderQty;
	@Builder
	public OrderDraftItemRequest(Long cartItemId, Long orderQty) {
		this.cartItemId = cartItemId;
		this.orderQty = orderQty;
	}
}
