package kr.hhplus.be.server.application.order;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import kr.hhplus.be.server.api.order.request.OrderDraftCreateRequest;
import kr.hhplus.be.server.api.order.response.OrderDraftResponse;
import kr.hhplus.be.server.util.ConstraintViolationUtils;
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
			if (!isCheckoutUniqueViolation(e)) {
				throw e;
			}
			// 기존 order 조회해서 업데이트한다.
			return orderQueryService.findByUserIdAndKey(request.getUserId(), request.getOrderSessionId());
		}
	}

	private boolean isCheckoutUniqueViolation(
		DataIntegrityViolationException e
	) {
		// getConstraintName()은 다음처럼 null일 수 있습니다. -> MySQL 통합 테스트로 반드시 확인
		return ConstraintViolationUtils
			.hasConstraint(
				e,
				"ux_order_user_checkout"
			);
	}
}
