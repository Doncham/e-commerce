package kr.hhplus.be.server.order.port.in;

import kr.hhplus.be.server.order.request.OrderDraftCreateRequest;
import kr.hhplus.be.server.order.response.OrderDraftCreateResponse;

public interface OrderUseCase {
	OrderDraftCreateResponse createOrder(OrderDraftCreateRequest request);
}
