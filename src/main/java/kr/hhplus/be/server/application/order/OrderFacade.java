package kr.hhplus.be.server.application.order;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import kr.hhplus.be.server.api.order.request.OrderDraftCreateRequest;
import kr.hhplus.be.server.api.order.response.OrderDraftCreateResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderFacade {
	private final OrderCommandService orderCommandService;
	private final OrderQueryService orderQueryService;

	public OrderDraftCreateResponse createOrder(OrderDraftCreateRequest request) {
		try{
			return orderCommandService.createOrder(request);
		} catch (DataIntegrityViolationException e) {
			return orderQueryService.findByUserIdAndKey(request.getUserId(), request.getIdempotencyKey());
		}
	}
}
