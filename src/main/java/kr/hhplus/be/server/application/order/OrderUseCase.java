package kr.hhplus.be.server.application.order;

import kr.hhplus.be.server.api.order.request.OrderDraftCreateRequest;
import kr.hhplus.be.server.api.order.response.OrderDraftResponse;

public interface OrderUseCase {
	OrderDraftResponse createOrUpdateOrder(OrderDraftCreateRequest request);
}
