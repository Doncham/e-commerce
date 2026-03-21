package kr.hhplus.be.server.application.order;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.domain.address.Address;
import kr.hhplus.be.server.domain.cartItem.CartItem;
import kr.hhplus.be.server.domain.inventoryReserve.InventoryReservation;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.point.Point;
import kr.hhplus.be.server.domain.point.exception.PointNotEnoughException;
import kr.hhplus.be.server.domain.point.exception.PointNotFoundException;
import kr.hhplus.be.server.domain.point.exception.PointUseNegativeException;
import kr.hhplus.be.server.domain.pointReservation.PointReservation;
import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.domain.inventory.Inventory;
import kr.hhplus.be.server.domain.inventory.exception.InSufficientStockException;
import kr.hhplus.be.server.domain.inventory.exception.NotFoundInventoryException;
import kr.hhplus.be.server.domain.order.ShippingInfo;
import kr.hhplus.be.server.api.order.request.OrderDraftCreateRequest;
import kr.hhplus.be.server.api.order.response.OrderDraftCreateResponse;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.domain.user.User;
import kr.hhplus.be.server.infrastructure.persistence.address.AddressRepository;
import kr.hhplus.be.server.infrastructure.persistence.cartItem.CartItemRepository;
import kr.hhplus.be.server.infrastructure.persistence.inventory.InventoryRepository;
import kr.hhplus.be.server.infrastructure.persistence.inventoryReserve.InventoryReserveRepository;
import kr.hhplus.be.server.infrastructure.persistence.order.OrderRepository;
import kr.hhplus.be.server.infrastructure.persistence.point.PointRepository;
import kr.hhplus.be.server.infrastructure.persistence.pointReservation.PointReservationRepository;
import kr.hhplus.be.server.infrastructure.persistence.user.UserRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderCommandService implements OrderUseCase {
	private final OrderRepository orderRepo;
	private final AddressRepository addressRepo;
	private final UserRepository userRepo;
	private final PointRepository pointRepo;
	private final CartItemRepository cartItemRepo;
	private final InventoryRepository inventoryRepo;

	private final InventoryReserveRepository inventoryReserveRepo;
	private final PointReservationRepository pointReservationRepo;

	@Transactional
	@Override
	public OrderDraftCreateResponse createOrder(OrderDraftCreateRequest request) {
		// JWT 대체 예정

		ShippingInfo shippingInfo = createShippingInfo(request.getAddressId());

		Order orderDraft = createOrderDraft(request.getUserId(), shippingInfo, request.getIdempotencyKey());

		// 사용할 포인트 검증
		Long pointUseAmount = normalizePointUseAmount(request.getPointUseAmount(), orderDraft.getId());

		// point 조회, 잔액 검증 및 예약
		// 락을 통해 동시성 관리: 포인트 충전, 포인트 사용, 만료 로직에서 임계영역인 point.balance를 보호함.
		reservePoint(request.getUserId(), pointUseAmount, orderDraft);

		// cart 조회할 때 product도 한번에 fetch 조인으로 가져오자
		List<CartItem> cartItems = cartItemRepo.findByCartIdWithProduct(request.getCartId());

		// productId를 뽑기
		List<Long> productsIds = extractSortedProductIds(cartItems);

		// 재고 조회 -> <productId, Inventory> 맵 생성
		//List<Inventory> inventories = inventoryPort.findByProductIdInForUpdateOrderByProductId(productsIds);
		Map<Long, Inventory> inventoryMap = lockInventoriesByProductId(productsIds);

		// 재고 검증 + 예약
		reserveInventories(cartItems, inventoryMap, orderDraft);

		// cartItem -> orderProduct 변환
		List<OrderProduct> orderProducts = createOrderProducts(cartItems);

		orderDraft.completeOrderDraft(orderProducts, request.getCouponId(), 0L, request.getMemo(), pointUseAmount);
		return OrderDraftCreateResponse.from(orderDraft);
	}

	private static List<OrderProduct> createOrderProducts(List<CartItem> cartItems) {
		List<OrderProduct> orderProducts = cartItems.stream()
			.flatMap(ci -> OrderProduct.createFromCartItem(ci).stream())
		.collect(Collectors.toList());
		return orderProducts;
	}

	private void reserveInventories(List<CartItem> cartItems, Map<Long, Inventory> inventoryMap, Order orderDraft) {
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
			inventoryReserveRepo.save(invReserve);
			inv.reserveStock(ci.getQty());
		}
	}

	private Map<Long, Inventory> lockInventoriesByProductId(List<Long> productsIds) {
		List<Inventory> inventories = inventoryRepo.findByProductIdInForUpdate(productsIds);
		Map<Long, Inventory> inventoryMap = inventories.stream()
			.collect(Collectors.toMap(inv -> inv.getProduct().getId(), inv -> inv));
		return inventoryMap;
	}

	private static List<Long> extractSortedProductIds(List<CartItem> cartItems) {
		List<Long> productsIds = cartItems.stream()
			.map(cartItem -> cartItem.getProduct().getId())
			.collect(Collectors.toList());
		// 데드락 방지를 위해 정렬 후 락을 건다.
		productsIds.sort(Long::compareTo);
		return productsIds;
	}

	private void reservePoint(Long userId, Long pointUseAmount, Order orderDraft) {
		Point point = pointRepo.findByUserIdForUpdate(userId)
			.orElseThrow(() -> new PointNotFoundException(ErrorCode.NOT_FOUND_POINT, userId));
		// 포인트 부족 예외
		if(point.availablePoint() < pointUseAmount) {
			throw new PointNotEnoughException(ErrorCode.NOT_ENOUGH_POINT, point.getId());
		}

		point.reservePoint(pointUseAmount);
		pointReservationRepo.save(
			PointReservation.reserve(orderDraft.getId(), userId, pointUseAmount)
		);
	}

	private Order createOrderDraft(Long userId, ShippingInfo shippingInfo, String idempotencyKey) {
		User user = userRepo.findById(userId)
			.orElseThrow(() -> new IllegalArgumentException("Invalid user ID"));
		Order draft = Order.createDraft(user, shippingInfo, idempotencyKey);
		// 동시 요청 시 유니크 예외 터지면서 OrderFacade에서 조회를 통해 정상 응답과 같은 응답을 해줌.
		Order orderDraft = orderRepo.saveAndFlush(draft);
		return orderDraft;
	}

	private static Long normalizePointUseAmount(Long pointUseAmount, Long orderDraftId) {
		if(pointUseAmount == null) pointUseAmount = 0L;
		if(pointUseAmount < 0)
			throw new PointUseNegativeException(ErrorCode.NEGATIVE_POINT_USE, orderDraftId);
		return pointUseAmount;
	}

	private ShippingInfo createShippingInfo(Long addressId) {
		Address address = addressRepo.findById(addressId)
			.orElseThrow(() -> new IllegalArgumentException("Invalid address ID"));
		ShippingInfo shippingInfo = new ShippingInfo(address);
		return shippingInfo;
	}
}
