package kr.hhplus.be.server.api.order.response;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import kr.hhplus.be.server.domain.order.Order;
import lombok.Builder;
import lombok.Getter;

@Getter
public class OrderDraftCreateResponse {
	private final Long orderId;
	private final String orderStatus;
	// addressId가 왜 필요함 주소를 반환해줘야지 ㅋㅋㅋ
	private final String address;
	private final String memo;
	// 쿠폰 적용 전 원래값
	private final Long itemTotal;
	private final int couponDiscount = 0;
	private final Long payAmount;
	private final List<OrderProductResponse> items;
	private final Long couponId;
	private final LocalDateTime createdAt;

	@Builder
	public OrderDraftCreateResponse(Long orderId, String orderStatus, String address, String memo, Long itemTotal,
		Long payAmount, List<OrderProductResponse> items, Long couponId, LocalDateTime createdAt) {
		this.orderId = orderId;
		this.orderStatus = orderStatus;
		this.address = address;
		this.memo = memo;
		this.itemTotal = itemTotal;
		this.payAmount = payAmount;
		this.items = items;
		this.couponId = couponId;
		this.createdAt = createdAt;
	}

	public static OrderDraftCreateResponse from(Order order){
		return OrderDraftCreateResponse.builder()
			.orderId(order.getId())
			.orderStatus(order.getStatus().toString())
			.address(order.getShippingAddress().getAddress())
			.memo(order.getMemo())
			.itemTotal(order.getItemTotal())
			.payAmount(order.getPayAmount())
			.couponId(order.getCouponId())
			.createdAt(order.getCreatedAt())
			.items(order.getOrderProducts().stream()
				.map(OrderProductResponse::from)
				.collect(Collectors.toList())
			)
			.build();
	}
}
