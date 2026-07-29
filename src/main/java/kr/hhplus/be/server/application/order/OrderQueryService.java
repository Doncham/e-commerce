package kr.hhplus.be.server.application.order;

import java.util.Optional;

import org.springframework.stereotype.Service;

import kr.hhplus.be.server.api.order.response.OrderDraftResponse;
import kr.hhplus.be.server.domain.order.Order;
import lombok.RequiredArgsConstructor;
@Service
@RequiredArgsConstructor
public class OrderQueryService {
	private final OrderRepository orderRepository;
	public OrderDraftResponse findByUserIdAndKey(Long userId, String idempotencyKey){
		Optional<Order> orderOpt = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
		return OrderDraftResponse.from(orderOpt.get());
	}
}
