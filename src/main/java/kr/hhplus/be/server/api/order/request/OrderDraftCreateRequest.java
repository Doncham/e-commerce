package kr.hhplus.be.server.api.order.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OrderDraftCreateRequest {
	@NotNull
	private Long addressId;
	private String memo;
	// 임시
	private Long userId;
	@PositiveOrZero
	private Long pointUseAmount;
	@NotBlank
	private String idempotencyKey;
	@NotEmpty
	private List<OrderDraftItemRequest> items;
	@Builder
	public OrderDraftCreateRequest(
		Long addressId,
		String memo,
		Long userId,
		Long pointUseAmount,
		String idempotencyKey,
		List<OrderDraftItemRequest> items
	) {
		this.addressId = addressId;
		this.memo = memo;
		this.userId = userId;
		this.pointUseAmount = pointUseAmount;
		this.idempotencyKey = idempotencyKey;
		this.items = items;
	}
	@Getter
	@NoArgsConstructor
	public static class OrderDraftItemRequest {
		@NotNull
		private Long cartItemId;
		private Long userCouponId;
		@Positive
		private Long orderQty;
		@Builder
		public OrderDraftItemRequest(Long cartItemId, Long userCouponId, Long orderQty) {
			this.cartItemId = cartItemId;
			this.userCouponId = userCouponId;
			this.orderQty = orderQty;
		}
	}
}
