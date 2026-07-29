package kr.hhplus.be.server.api.order.response;

import java.time.LocalDateTime;
import java.util.List;

import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.OrderStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
public class OrderDraftResponse {
	private final Long orderId;
	private final OrderStatus orderStatus;
	// addressId가 왜 필요함 주소를 반환해줘야지 ㅋㅋㅋ
	private final String address;
	private final String memo;
	// 쿠폰 적용 전 원래값
	private final Long itemTotal;
	private final Long paymentAmount;
	private final List<OrderProductResponse> items;
	private final LocalDateTime createdAt;

	@Builder
	public OrderDraftResponse(Long orderId, OrderStatus orderStatus, String address, String memo, Long itemTotal,
		Long paymentAmount, List<OrderProductResponse> items, LocalDateTime createdAt) {
		this.orderId = orderId;
		this.orderStatus = orderStatus;
		this.address = address;
		this.memo = memo;
		this.itemTotal = itemTotal;
		this.paymentAmount = paymentAmount;
		this.items = items;
		this.createdAt = createdAt;
	}

	public static OrderDraftResponse from(Order order){
		return OrderDraftResponse.builder()
			.orderId(order.getId())
			.orderStatus(order.getStatus())
			.address(order.getShippingAddress().getAddress())
			.memo(order.getMemo())
			.itemTotal(order.getItemTotal())
			.paymentAmount(order.getPaymentAmount())
			.createdAt(order.getCreatedAt())
			.items(order.getOrderProducts().stream()
				.map(OrderProductResponse::from)
				.toList()
			)
			.build();
	}
}
