package kr.hhplus.be.server.order.adapter;

import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.order.Order;
import kr.hhplus.be.server.order.OrderRepository;
import kr.hhplus.be.server.order.exception.OrderNotFoundException;
import kr.hhplus.be.server.order.port.out.OrderPort;
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

}
