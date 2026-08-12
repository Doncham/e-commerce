package kr.hhplus.be.server.api.order.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
	private String orderSessionId;
	@NotEmpty
	@Valid // 객체 안쪽까지 들어가서 검증해라
	private List<OrderDraftItemRequest> items;
	@Builder
	public OrderDraftCreateRequest(
		Long addressId,
		String memo,
		Long userId,
		// Long pointUseAmount,
		String orderSessionId,
		List<OrderDraftItemRequest> items
	) {
		this.addressId = addressId;
		this.memo = memo;
		this.userId = userId;
		//this.pointUseAmount = pointUseAmount;
		this.orderSessionId = orderSessionId;
		this.items = items;
	}
}
