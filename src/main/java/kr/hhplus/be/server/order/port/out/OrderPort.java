package kr.hhplus.be.server.order.port.out;

import kr.hhplus.be.server.order.Order;

public interface OrderPort {
	Order loadOrderForUpdate(Long orderId);
	Order save(Order order);
}
