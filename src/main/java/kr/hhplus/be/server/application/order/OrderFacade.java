package kr.hhplus.be.server.application.order;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import kr.hhplus.be.server.api.order.request.OrderDraftCreateRequest;
import kr.hhplus.be.server.api.order.response.OrderDraftResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderFacade {
	private final OrderCommandService orderCommandService;
	private final OrderQueryService orderQueryService;

	public OrderDraftResponse createOrUpdateOrder(OrderDraftCreateRequest request) {
		try{
			return orderCommandService.createOrUpdateOrder(request);
		} catch (DataIntegrityViolationException e) {
			return orderQueryService.findByUserIdAndKey(request.getUserId(), request.getCheckoutId());
		}
	}
}
