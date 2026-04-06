package kr.hhplus.be.server.application.order;

import java.util.Optional;

import org.springframework.stereotype.Service;

import kr.hhplus.be.server.api.order.response.OrderCreateResponse;
import kr.hhplus.be.server.domain.order.Order;
import lombok.RequiredArgsConstructor;
@Service
@RequiredArgsConstructor
public class OrderQueryService {
	private final OrderRepository orderRepository;
	public OrderCreateResponse findByUserIdAndKey(Long userId, String idempotencyKey){
		Optional<Order> orderOpt = orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
		return OrderCreateResponse.from(orderOpt.get());
	}
}
