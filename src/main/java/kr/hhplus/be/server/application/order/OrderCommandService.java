package kr.hhplus.be.server.application.order;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.api.order.request.OrderDraftCreateRequest;
import kr.hhplus.be.server.api.order.response.OrderDraftResponse;
import kr.hhplus.be.server.domain.address.Address;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.ShippingInfo;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.domain.user.User;
import kr.hhplus.be.server.exception.BusinessException;
import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.infrastructure.persistence.address.AddressRepository;
import kr.hhplus.be.server.infrastructure.persistence.order.OrderRepository;
import kr.hhplus.be.server.infrastructure.persistence.user.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderCommandService implements OrderUseCase {
	private final OrderRepository orderRepo;
	private final AddressRepository addressRepo;
	private final UserRepository userRepo;

	private final orderDraftItemResolver orderDraftItemResolver;
	private final ActivePaymentCleanupService activePaymentCleanupService;

	@Transactional
	@Override
	public OrderDraftResponse createOrUpdateOrder(
		OrderDraftCreateRequest request
	) {
		// 추후 request가 아니라 인증 객체에서 가져와야 함
		Long userId = request.getUserId();

		// validateRequest(request); controller에서 받아올 때 검증 진행함.

		// order 조회 or 생성
		// 비관적락으로 조회하긴 하는데 이게 멱등적인건가?
		// update 로직이라서 논리적으로 같은 요청이면 update 시 결과는 항상 같음
		// 요청 A와 요청 B가 처음 조회 시 결과 없음을 조회하고 둘 다 createOrderDraft로 order 생성 -> 유니크 제약 걸림.
		// Facade에서 잡아서 처리.
		Order order = findOrCreateOrder(
			userId,
			request.getCheckoutId()
		);

		// draft -> 그냥 수정
		// payment_pending -> 예약 해제 후 수정
		prepareOrderForModification(order);

		ShippingInfo shippingInfo = createShippingInfo(
			userId,
			request.getAddressId()
		);

		// orderProduct 뽑아오면서 검증하나보네
		List<OrderProduct> orderProducts =
			orderDraftItemResolver.resolve(
				userId,
				request.getItems()
			);

		order.updateOrderDraft(
			orderProducts,
			request.getMemo(),
			shippingInfo
		);

		return OrderDraftResponse.from(order);
	}

	/**
	 * DRAFT는 바로 수정한다.
	 * PAYMENT_PENDING은 활성 Payment를 정리한 뒤 수정한다.
	 * 완료되거나 취소된 주문은 수정할 수 없다.
	 */
	private void prepareOrderForModification(Order order) {
		if (order.isDraft()) {
			return;
		}

		// 예약 싹 취소해줘야지
		if (order.isPaymentPending()) {
			activePaymentCleanupService.cleanupForOrderChange(order);
			return;
		}

		throw new BusinessException(
			ErrorCode.ORDER_NOT_MODIFIABLE,
			"Order cannot be modified. "
				+ "orderId=" + order.getId()
				+ ", status=" + order.getStatus()
		);
	}

	private Order findOrCreateOrder(
		Long userId,
		String checkoutId
	) {
		return orderRepo
			.findByUserIdAndCheckoutIdForUpdate(
				userId,
				checkoutId
			)
			.orElseGet(() ->
				createOrderDraft(userId, checkoutId)
			);
	}

	private Order createOrderDraft(
		Long userId,
		String checkoutId
	) {
		User user = userRepo.findById(userId)
			.orElseThrow(() ->
				BusinessException.of(
					ErrorCode.USER_NOT_FOUND,
					"User not found. userId=" + userId
				)
			);

		Order order = Order.createDraft(
			user,
			checkoutId
		);

		return orderRepo.save(order);
	}


	private ShippingInfo createShippingInfo(
		Long userId,
		Long addressId
	) {
		Address address = addressRepo
			.findByIdAndUserId(addressId, userId)
			.orElseThrow(() ->
				new BusinessException(
					ErrorCode.ADDRESS_NOT_FOUND,
					"Address not found or not owned by user. "
						+ "addressId=" + addressId
						+ ", userId=" + userId
				)
			);

		return new ShippingInfo(address);
	}
}
