package kr.hhplus.be.server.payment.port.out;

import kr.hhplus.be.server.order.Order;

public interface LoadOrderPort {
	Order loadOrderForUpdate(Long orderId);
}
