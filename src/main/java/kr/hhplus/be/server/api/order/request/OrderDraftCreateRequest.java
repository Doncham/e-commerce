package kr.hhplus.be.server.api.order.request;

import java.util.List;

import jakarta.validation.Valid;
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
	// @PositiveOrZero
	// private Long pointUseAmount;
	@NotBlank
	private String checkoutId;
	@NotEmpty
	@Valid // 객체 안쪽까지 들어가서 검증해라
	private List<OrderDraftItemRequest> items;
	@Builder
	public OrderDraftCreateRequest(
		Long addressId,
		String memo,
		Long userId,
		// Long pointUseAmount,
		String checkoutId,
		List<OrderDraftItemRequest> items
	) {
		this.addressId = addressId;
		this.memo = memo;
		this.userId = userId;
		//this.pointUseAmount = pointUseAmount;
		this.checkoutId = checkoutId;
		this.items = items;
	}
}
