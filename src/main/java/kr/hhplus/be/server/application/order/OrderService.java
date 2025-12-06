package kr.hhplus.be.server.application.order;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.domain.address.Address;
import kr.hhplus.be.server.domain.cartItem.CartItem;
import kr.hhplus.be.server.application.cartItem.CartItemPort;
import kr.hhplus.be.server.domain.order.Order;
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
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService implements OrderUseCase {
	private final OrderPort orderPort;
	private final AddressPort addressPort;
	private final UserPort userPort;
	private final CartItemPort cartItemPort;
	private final InventoryPort inventoryPort;

	@Transactional
	@Override
	public OrderDraftCreateResponse createOrder(OrderDraftCreateRequest request) {
		Long addressId = request.getAddressId();
		String memo = request.getMemo();
		Long cartId = request.getCartId();
		// JWT 대체 예정
		Long userId = request.getUserId();
		Long couponId = request.getCouponId();

		Address address = addressPort.loadAddress(addressId);
		ShippingInfo shippingInfo = new ShippingInfo(address);

		User user = userPort.loadUser(userId);

		// cart 조회할 때 product도 한번에 fetch 조인으로 가져오자
		List<CartItem> cartItems = cartItemPort.findByCartIdWithProduct(cartId);

		// productId를 뽑기
		List<Long> productsIds = cartItems.stream()
			.map(cartItem -> cartItem.getProduct().getId())
			.collect(Collectors.toList());

		List<Inventory> inventories = inventoryPort.findByProductIdIn(productsIds);
		// <productId, Inventory> 맵 생성
		Map<Long, Inventory> inventoryMap = inventories.stream()
			.collect(Collectors.toMap(inv -> inv.getProduct().getId(), inv -> inv));

		// 재고 검증 + reserved 증가
		for(CartItem ci : cartItems) {
			Inventory inv = inventoryMap.get(ci.getProduct().getId());
			if(inv == null){
				throw new NotFoundInventoryException(ci.getProduct().getId().toString());
			}
			if(inv.availableStock() - ci.getQty() < 0 ){
				throw new InSufficientStockException(ErrorCode.NOT_SUFFICIENT_STOCK, ci.getProduct().getId().toString());
			}
			inv.reserveStock(ci.getQty());
		}
		// cartItem -> orderProduct 변환
		List<OrderProduct> orderProducts = cartItems.stream().map(ci ->
			// cartItem.getProduct().getId()로 inventory 조회 -> 수량 확인 -> 재고 부족 시 예외 처리
			OrderProduct.create(ci.getProduct().getId(),
				ci.getProduct().getName(),
				ci.getProduct().getPrice(),
				ci.getQty()
			)
		).collect(Collectors.toList());

		Order order = Order.createOrder(user, shippingInfo, orderProducts, couponId, 0L, memo);
		orderPort.save(order);

		return OrderDraftCreateResponse.from(order);

	}
}
