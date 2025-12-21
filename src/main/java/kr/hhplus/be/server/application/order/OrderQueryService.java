package kr.hhplus.be.server.application.order;

import java.util.Optional;

import org.springframework.stereotype.Service;

import kr.hhplus.be.server.api.order.response.OrderDraftCreateResponse;
import kr.hhplus.be.server.domain.order.Order;
import lombok.RequiredArgsConstructor;
@Service
@RequiredArgsConstructor
public class OrderQueryService {
	private OrderPort orderPort;
	public OrderDraftCreateResponse findByUserIdAndKey(Long userId, String idempotencyKey){
		Optional<Order> orderOpt = orderPort.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
		return OrderDraftCreateResponse.from(orderOpt.get());
	}
}
