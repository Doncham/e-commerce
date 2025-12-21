package kr.hhplus.be.server.infrastructure.persistence.order;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.exception.OrderNotFoundException;
import kr.hhplus.be.server.application.order.OrderPort;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OrderJpaAdapter implements OrderPort {
	private final OrderRepository orderRepository;

	@Override
	public Order loadOrderForUpdate(Long orderId) {
		return orderRepository.findByIdForUpdate(orderId)
			.orElseThrow(() -> new OrderNotFoundException(ErrorCode.NOT_FOUND_ORDER, orderId));
	}

	@Override
	public Order save(Order order) {
		return orderRepository.save(order);
	}

	@Override
	public Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey) {
		return orderRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey);
	}

	@Override
	public Order saveAndFlush(Order draft) {
		return orderRepository.saveAndFlush(draft);
	}

}
