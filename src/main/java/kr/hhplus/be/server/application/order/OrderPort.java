package kr.hhplus.be.server.application.order;

import kr.hhplus.be.server.domain.order.Order;

public interface OrderPort {
	Order loadOrderForUpdate(Long orderId);
	Order save(Order order);
}
