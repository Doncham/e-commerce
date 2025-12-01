package kr.hhplus.be.server.payment.adapter;

import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.order.Order;
import kr.hhplus.be.server.order.OrderRepository;
import kr.hhplus.be.server.order.exception.OrderNotFoundException;
import kr.hhplus.be.server.payment.port.out.LoadOrderPort;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class OrderJpaAdapter implements LoadOrderPort {
	private final OrderRepository orderRepository;

	@Override
	public Order loadOrderForUpdate(Long orderId) {
		return orderRepository.findByIdForUpdate(orderId)
			.orElseThrow(() -> new OrderNotFoundException(ErrorCode.NOT_FOUND_ORDER, orderId));
	}

}
