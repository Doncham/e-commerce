package kr.hhplus.be.server.application.order;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.application.point.PointPort;
import kr.hhplus.be.server.domain.address.Address;
import kr.hhplus.be.server.domain.cartItem.CartItem;
import kr.hhplus.be.server.application.cartItem.CartItemPort;
import kr.hhplus.be.server.domain.inventoryReserve.InventoryReservation;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.point.Point;
import kr.hhplus.be.server.domain.point.exception.PointNotEnoughException;
import kr.hhplus.be.server.domain.point.exception.PointUseNegativeException;
import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.domain.inventory.Inventory;
import kr.hhplus.be.server.domain.inventory.exception.InSufficientStockException;
import kr.hhplus.be.server.domain.inventory.exception.NotFoundInventoryException;
import kr.hhplus.be.server.application.address.AddressPort;
import kr.hhplus.be.server.application.inventory.InventoryPort;
import kr.hhplus.be.server.domain.order.ShippingInfo;
import kr.hhplus.be.server.api.order.request.OrderDraftCreateRequest;
import kr.hhplus.be.server.api.order.response.OrderDraftCreateResponse;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.domain.user.User;
import kr.hhplus.be.server.domain.user.UserPort;
import kr.hhplus.be.server.infrastructure.persistence.inventoryReserve.InventoryReserveRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderCommandService implements OrderUseCase {
	private final OrderPort orderPort;
	private final AddressPort addressPort;
	private final UserPort userPort;
	private final CartItemPort cartItemPort;
	private final InventoryPort inventoryPort;
	private final PointPort pointPort;
	private final InventoryReserveRepository inventoryReserveRepository;

	@Transactional
	@Override
	public OrderDraftCreateResponse createOrder(OrderDraftCreateRequest request) {
		Long addressId = request.getAddressId();
		String memo = request.getMemo();
		Long cartId = request.getCartId();
		// JWT 대체 예정
		Long userId = request.getUserId();
		Long couponId = request.getCouponId();
		String idempotencyKey = request.getIdempotencyKey();

		Address address = addressPort.loadAddress(addressId);
		ShippingInfo shippingInfo = new ShippingInfo(address);

		User user = userPort.loadUser(userId);
		Order draft = Order.createDraft(user, shippingInfo, idempotencyKey);
		// 동시 요청 시 유니크 예외 터지면서 OrderFacade에서 조회를 통해 정상 응답과 같은 응답을 해줌.
		Order orderDraft = orderPort.saveAndFlush(draft);

		// 사용할 포인트 검증
		Long pointUseAmount = request.getPointUseAmount();
		if(pointUseAmount == null) pointUseAmount = 0L;
		if(pointUseAmount < 0)
			throw new PointUseNegativeException(ErrorCode.NEGATIVE_POINT_USE, orderDraft.getId());

		// point 조회 및 검증
		// 락을 통해 동시성 관리: 포인트 충전, 포인트 사용, 만료 로직에서 임계영역인 balance를 보호함.
		Point point = pointPort.findByUserIdForUpdate(userId);
		// 포인트 부족 예외
		if(point.availablePoint() < pointUseAmount) {
			throw new PointNotEnoughException(ErrorCode.NOT_ENOUGH_POINT, point.getId());
		}
		// point 예약 + order에 point 관련 필드 넣기
		point.reservePoint(pointUseAmount);

		// cart 조회할 때 product도 한번에 fetch 조인으로 가져오자
		List<CartItem> cartItems = cartItemPort.findByCartIdWithProduct(cartId);

		// productId를 뽑기
		List<Long> productsIds = cartItems.stream()
			.map(cartItem -> cartItem.getProduct().getId())
			.collect(Collectors.toList());
		// 데드락 방지를 위해 정렬 후 락을 건다.
		productsIds.sort(Long::compareTo);

		List<Inventory> inventories = inventoryPort.findByProductIdInForUpdateOrderByProductId(productsIds);
		// <productId, Inventory> 맵 생성
		Map<Long, Inventory> inventoryMap = inventories.stream()
			.collect(Collectors.toMap(inv -> inv.getProduct().getId(), inv -> inv));

		// 재고 검증 + reserved 증가
		for(CartItem ci : cartItems) {
			// cartItem.getProduct().getId()로 inventory 조회 -> 수량 확인 -> 재고 부족 시 예외 처리
			Inventory inv = inventoryMap.get(ci.getProduct().getId());
			if(inv == null){
				throw new NotFoundInventoryException(ci.getProduct().getId().toString());
			}
			if(inv.availableStock() - ci.getQty() < 0 ){
				throw new InSufficientStockException(ErrorCode.NOT_SUFFICIENT_STOCK, ci.getProduct().getId().toString());
			}
			// 재고 차감 예약
			InventoryReservation invReserve = InventoryReservation.reserve(orderDraft.getId(), inv.getId(), ci.getQty());
			inventoryReserveRepository.save(invReserve);
			inv.reserveStock(ci.getQty());
		}
		// cartItem -> orderProduct 변환
		List<OrderProduct> orderProducts = cartItems.stream().map(ci ->
			OrderProduct.create(ci.getProduct().getId(),
				ci.getProduct().getName(),
				ci.getProduct().getPrice(),
				ci.getQty()
			)
		).collect(Collectors.toList());

		orderDraft.completeOrderDraft(orderProducts, couponId, 0L, memo, pointUseAmount);
		return OrderDraftCreateResponse.from(orderDraft);
	}
}
