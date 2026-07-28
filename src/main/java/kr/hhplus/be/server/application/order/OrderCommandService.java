package kr.hhplus.be.server.application.order;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.api.order.request.OrderDraftCreateRequest;
import kr.hhplus.be.server.api.order.request.OrderDraftItemRequest;
import kr.hhplus.be.server.api.order.response.OrderCreateResponse;
import kr.hhplus.be.server.domain.address.Address;
import kr.hhplus.be.server.domain.cartItem.CartItem;
import kr.hhplus.be.server.domain.inventory.Inventory;
import kr.hhplus.be.server.domain.inventory.exception.InSufficientStockException;
import kr.hhplus.be.server.domain.inventory.exception.NotFoundInventoryException;
import kr.hhplus.be.server.domain.inventoryReserve.InventoryReservation;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.domain.order.ShippingInfo;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.domain.point.Point;
import kr.hhplus.be.server.domain.point.exception.PointNotEnoughException;
import kr.hhplus.be.server.domain.point.exception.PointNotFoundException;
import kr.hhplus.be.server.domain.point.exception.PointUseNegativeException;
import kr.hhplus.be.server.domain.pointReservation.PointReservation;
import kr.hhplus.be.server.domain.user.User;
import kr.hhplus.be.server.exception.ErrorCode;
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
	public OrderCreateResponse createOrUpdateOrder(OrderDraftCreateRequest request) {
		// JWT 대체 예정
		Long userId = request.getUserId();

		ShippingInfo shippingInfo = createShippingInfo(request.getAddressId());

		// 기존 order가 존재하는지 확인
		String checkoutId = request.getCheckoutId();
		Optional<Order> optOrder = orderRepo.findByUserIdAndCheckoutId(userId, checkoutId);
		if(optOrder.isPresent()) {

			Order order = optOrder.get();
			// draft 상태가 아니면 어떻게 해야하는거지? 예외이지?
			if(!order.getStatus().equals(OrderStatus.DRAFT)) {
				// 예외 던지기?
			}

			// update 로직
			Map<Long, OrderDraftItemRequest> reqItemMap = getReqInfoMapByCartItemId(request);
			List<CartItem> cartItems = getVerifiedCartItems(request, userId);
			List<OrderProduct> orderProducts = createOrderProducts(cartItems, reqItemMap);

			order.updateOrderDraft(orderProducts, request.getMemo(), shippingInfo);

			// 응답 반환
			return OrderCreateResponse.from(order);
		}
		// 없으면 새로 만들기
		// 여기서 Unique 예외 시 어떤 응답을 사용자에게 줄 건가요?
		Order orderDraft = createOrderDraft(userId, shippingInfo, request.getCheckoutId());

		// 사용할 포인트 검증
		// Long pointUseAmount = normalizePointUseAmount(request.getPointUseAmount(), orderDraft.getId());

		// <carItemId, (qty, couponId)>
		Map<Long, OrderDraftItemRequest> reqItemInfoMap = getReqInfoMapByCartItemId(
			request);

		// cartItem 조회 + 유저 검증
		List<CartItem> cartItems = getVerifiedCartItems(request, userId);

		// productId를 뽑기
		List<Long> productsIds = extractSortedProductIds(cartItems);

		// 재고 조회 -> <productId, Inventory> 맵 생성
		//List<Inventory> inventories = inventoryPort.findByProductIdInForUpdateOrderByProductId(productsIds);
		// 재고 조회 -> 검증까지만 하는 메서드 필요
		Map<Long, Inventory> inventoryMap = getProductIdToInventoryMap(productsIds);
		validateInventories(cartItems, inventoryMap, reqItemInfoMap);

		// 재고 검증 + 예약
		// 이동 예정
		// reserveInventories(cartItems, inventoryMap, orderDraft, reqItemInfoMap);

		// cartItem -> orderProduct 변환
		List<OrderProduct> orderProducts = createOrderProducts(cartItems, reqItemInfoMap);

		// point 조회, 잔액 검증 및 예약
		// 락을 통해 동시성 관리: 포인트 충전, 포인트 사용, 만료 로직에서 임계영역인 point.balance를 보호함.
		// 이동 예정
		// reservePoint(userId, pointUseAmount, orderDraft);

		// 포인트 배분
		// 이동 예정
		// PointAllocator.allocatePoints(orderProducts, pointUseAmount);

		orderDraft.updateOrderDraft(orderProducts, request.getMemo(), shippingInfo);
		return OrderCreateResponse.from(orderDraft);
	}

	private List<CartItem> getVerifiedCartItems(OrderDraftCreateRequest request, Long userId) {
		List<Long> cartItemIds = request.getItems().stream()
			.map(OrderDraftItemRequest::getCartItemId)
			.toList();

		List<CartItem> cartItems = cartItemRepo.findAllByIdInWithProductCartUser(cartItemIds);

		if (cartItems.size() != cartItemIds.size()) {
			throw new IllegalArgumentException("주문할 수 없는 장바구니 상품이 포함되어 있습니다.");
		}

		for (CartItem cartItem : cartItems) {
			// Cart 소유자 검증
			// NPE 발생 가능하니까 == 으로 비교해주는게 좋을까?
			if(!cartItem.getCart().getUser().getId().equals(userId)) {
				throw new IllegalArgumentException("해당 유저의 장바구니에 담긴 상품이 아닙니다.");
			}
			// coupon 유효성 검증(아직 없음)
		}
		return cartItems;
	}

	private static Map<Long, OrderDraftItemRequest> getReqInfoMapByCartItemId(
		OrderDraftCreateRequest request) {
		Map<Long, OrderDraftItemRequest> reqItemInfoMap = request.getItems().stream()
			.collect(Collectors.toMap(
				OrderDraftItemRequest::getCartItemId,
				item -> item
			));
		return reqItemInfoMap;
	}

	// 포인트랑, 쿠폰 정보도 orderProduct에 넘겨줘야함.
	private static List<OrderProduct> createOrderProducts(List<CartItem> cartItems, Map<Long, OrderDraftItemRequest> reqItemMap) {
		// 왜 flatMap을 써야하지? createFromCartItem의 반환값이 List임.
		List<OrderProduct> orderProducts = cartItems.stream()
			.flatMap(ci -> {
				OrderDraftItemRequest reqItem = reqItemMap.get(ci.getId());
				return OrderProduct.createFromCartItem(ci, reqItem.getOrderQty()).stream();
			})
		.collect(Collectors.toList());
		return orderProducts;
	}

	private void validateInventories(List<CartItem> cartItems, Map<Long, Inventory> inventoryMap, Map<Long, OrderDraftItemRequest> reqItemMap) {
		for(CartItem ci : cartItems) {
			// cartItem.getProduct().getId()로 inventory 조회 -> 수량 확인 -> 재고 부족 시 예외 처리
			Long orderQty = reqItemMap.get(ci.getId()).getOrderQty();
			if (orderQty <= 0) {
				throw new IllegalArgumentException("주문 수량은 1 이상이어야 합니다. cartItemId=" + ci.getId());
			}
			// 프론트에서 건넨 주문 수량이랑 실제 DB에 기록된 수량을 비교하는 코드(CartItem이 DB에 저장되니까)
			// 그럼 이거는 == 비교해야하는거 아님?
			if (orderQty > ci.getQty()) {
				throw new IllegalArgumentException("주문 수량이 장바구니 수량을 초과했습니다. cartItemId=" + ci.getId());
			}

			Inventory inv = inventoryMap.get(ci.getProduct().getId());
			if(inv == null){
				throw new NotFoundInventoryException(ci.getProduct().getId().toString());
			}
			if(inv.availableStock() - orderQty < 0 ){
				throw new InSufficientStockException(ErrorCode.NOT_SUFFICIENT_STOCK, ci.getProduct().getId().toString());
			}
		}
	}

	private void reserveInventories(List<CartItem> cartItems, Map<Long, Inventory> inventoryMap, Order orderDraft, Map<Long, OrderDraftItemRequest> reqItemMap) {
		for(CartItem ci : cartItems) {
			// cartItem.getProduct().getId()로 inventory 조회 -> 수량 확인 -> 재고 부족 시 예외 처리
			Long orderQty = reqItemMap.get(ci.getId()).getOrderQty();
			if (orderQty <= 0) {
				throw new IllegalArgumentException("주문 수량은 1 이상이어야 합니다. cartItemId=" + ci.getId());
			}
			// 프론트에서 건넨 주문 수량이랑 실제 DB에 기록된 수량을 비교하는 코드(CartItem이 DB에 저장되니까)
			// 그럼 이거는 == 비교해야하는거 아님?
			if (orderQty > ci.getQty()) {
				throw new IllegalArgumentException("주문 수량이 장바구니 수량을 초과했습니다. cartItemId=" + ci.getId());
			}

			Inventory inv = inventoryMap.get(ci.getProduct().getId());
			if(inv == null){
				throw new NotFoundInventoryException(ci.getProduct().getId().toString());
			}
			if(inv.availableStock() - orderQty < 0 ){
				throw new InSufficientStockException(ErrorCode.NOT_SUFFICIENT_STOCK, ci.getProduct().getId().toString());
			}
			// 재고 차감 예약
			InventoryReservation invReserve = InventoryReservation.reserve(orderDraft.getId(), inv.getId(), orderQty);
			inventoryReserveRepo.save(invReserve);
			inv.reserveStock(orderQty);
		}
	}

	private Map<Long, Inventory> lockInventoriesByProductId(List<Long> productsIds) {
		List<Inventory> inventories = inventoryRepo.findByProductIdInForUpdate(productsIds);
		Map<Long, Inventory> inventoryMap = inventories.stream()
			.collect(Collectors.toMap(inv -> inv.getProduct().getId(), inv -> inv));
		return inventoryMap;
	}

	private Map<Long, Inventory> getProductIdToInventoryMap(List<Long> productsIds) {
		List<Inventory> inventories = inventoryRepo.findByProductIdIn(productsIds);
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
		if(pointUseAmount == 0) return;

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

	private Order createOrderDraft(Long userId, ShippingInfo shippingInfo, String checkoutId) {
		User user = userRepo.findById(userId)
			.orElseThrow(() -> new IllegalArgumentException("Invalid user ID"));
		// 그냥 바로 만드는게 아니라 idemKey로 조회해서 있으면 그걸 반환하면 될듯?
		Order draft = Order.createDraft(user, checkoutId);
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
