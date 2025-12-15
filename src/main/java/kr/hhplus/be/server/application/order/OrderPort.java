package kr.hhplus.be.server.application.order;

import java.util.Optional;

import kr.hhplus.be.server.domain.order.Order;

public interface OrderPort {
	Order loadOrderForUpdate(Long orderId);
	Order save(Order order);

	Optional<Order> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

	Order saveAndFlush(Order draft);
}
