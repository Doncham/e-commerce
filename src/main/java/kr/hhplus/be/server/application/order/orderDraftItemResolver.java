package kr.hhplus.be.server.application.order;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import kr.hhplus.be.server.api.order.request.OrderDraftItemRequest;
import kr.hhplus.be.server.domain.cartItem.CartItem;
import kr.hhplus.be.server.domain.inventory.Inventory;
import kr.hhplus.be.server.domain.inventory.exception.InSufficientStockException;
import kr.hhplus.be.server.domain.inventory.exception.NotFoundInventoryException;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.exception.BusinessException;
import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.infrastructure.persistence.cartItem.CartItemRepository;
import kr.hhplus.be.server.infrastructure.persistence.inventory.InventoryRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class orderDraftItemResolver {
	private final CartItemRepository cartItemRepository;
	private final InventoryRepository inventoryRepository;

	public List<OrderProduct> resolve(
		Long userId,
		List<OrderDraftItemRequest> requests
	) {
		validateDuplicateCartItems(requests);

		Map<Long, OrderDraftItemRequest> requestByCartItemId =
			createRequestMap(requests);

		List<CartItem> cartItems = findCartItems(
			requestByCartItemId.keySet()
		);

		// 해당 userId와 cartItem의 주인 비교, 프론트가 넘긴 수량값과 실제 DB 수량 비교
		validateCartItems(
			userId,
			cartItems,
			requestByCartItemId
		);

		// 예약하지는 않지만 재고 수량 정도는 확인하는구나
		validateCurrentInventories(
			cartItems,
			requestByCartItemId
		);

		return createOrderProducts(
			cartItems,
			requestByCartItemId
		);
	}

	private void validateDuplicateCartItems(
		List<OrderDraftItemRequest> requests
	) {
		Set<Long> cartItemIds = new HashSet<>();

		for (OrderDraftItemRequest request : requests) {
			if (!cartItemIds.add(request.getCartItemId())) {
				throw BusinessException.of(
					ErrorCode.DUPLICATE_ORDER_ITEM,
					"Duplicate cartItemId. cartItemId="
						+ request.getCartItemId()
				);
			}
		}
	}



	private Map<Long, OrderDraftItemRequest> createRequestMap(
		List<OrderDraftItemRequest> requests
	) {
		return requests.stream()
			.collect(Collectors.toMap(
				OrderDraftItemRequest::getCartItemId,
				Function.identity()
			));
	}

	private List<CartItem> findCartItems(
		Set<Long> cartItemIds
	) {
		List<CartItem> cartItems =
			cartItemRepository
				.findAllByIdInWithProductCartUser(
					List.copyOf(cartItemIds)
				);

		if (cartItems.size() != cartItemIds.size()) {
			throw BusinessException.of(
				ErrorCode.INVALID_ORDER_ITEM,
				"Some cart items do not exist. "
					+ "requestedCount="
					+ cartItemIds.size()
					+ ", foundCount="
					+ cartItems.size()
			);
		}

		return cartItems;
	}

	private void validateCartItems(
		Long userId,
		List<CartItem> cartItems,
		Map<Long, OrderDraftItemRequest> requestByCartItemId
	) {
		for (CartItem cartItem : cartItems) {
			validateCartItemOwner(
				userId,
				cartItem
			);

			validateOrderQuantity(
				cartItem,
				requestByCartItemId.get(
					cartItem.getId()
				)
			);

			validateProductOrderable(cartItem);
		}
	}

	private void validateCartItemOwner(
		Long userId,
		CartItem cartItem
	) {
		Long ownerId = cartItem.getCart()
			.getUser()
			.getId();

		/*
		 * Long 비교는 equals 또는 Objects.equals를 사용한다.
		 * userId가 인증 정보에서 오면 null이 아니므로
		 * userId.equals(ownerId)가 안전하다.
		 */
		if (!userId.equals(ownerId)) {
			throw BusinessException.of(
				ErrorCode.INVALID_ORDER_ITEM,
				"Cart item is not owned by user. "
					+ "cartItemId=" + cartItem.getId()
					+ ", userId=" + userId
			);
		}
	}

	private void validateOrderQuantity(
		CartItem cartItem,
		OrderDraftItemRequest request
	) {
		// 장바구니 중 일부를 주문할 수 있다.
		if (request.getOrderQty()
			> cartItem.getQty()) {
			throw BusinessException.of(
				ErrorCode.ORDER_QUANTITY_EXCEEDS_CART,
				"Order quantity exceeds cart quantity. "
					+ "cartItemId=" + cartItem.getId()
					+ ", orderQty="
					+ request.getOrderQty()
					+ ", cartQty="
					+ cartItem.getQty()
			);
		}
	}

	private void validateProductOrderable(
		CartItem cartItem
	) {
		if (!cartItem.getProduct().isOrderable()) {
			throw BusinessException.of(
				ErrorCode.PRODUCT_NOT_ORDERABLE,
				"Product is not orderable. productId="
					+ cartItem.getProduct().getId()
			);
		}
	}

	/**
	 * 주문하기 시점의 참고 검증이다.
	 *
	 * 실제 재고 확보는 payments/prepare에서
	 * 비관적 락과 함께 다시 검증하고 예약해야 한다.
	 */
	private void validateCurrentInventories(
		List<CartItem> cartItems,
		Map<Long, OrderDraftItemRequest> requestByCartItemId
	) {

		List<Long> productIds = cartItems.stream()
			.map(cartItem ->
				cartItem.getProduct().getId()
			)
			.sorted()
			.toList();

		Map<Long, Inventory> inventoryByProductId =
			inventoryRepository
				.findByProductIdIn(productIds)
				.stream()
				.collect(Collectors.toMap(
					inventory ->
						inventory.getProduct().getId(),
					Function.identity()
				));

		for (CartItem cartItem : cartItems) {
			Long productId =
				cartItem.getProduct().getId();

			long orderQty = requestByCartItemId
				.get(cartItem.getId())
				.getOrderQty();

			Inventory inventory =
				inventoryByProductId.get(productId);

			if (inventory == null) {
				throw new IllegalStateException(
					"Inventory does not exist. productId="
						+ productId
				);
			}

			if (inventory.availableStock()
				< orderQty) {
				throw BusinessException.of(
					ErrorCode.STOCK_NOT_SUFFICIENT,
					"Insufficient stock. "
						+ "productId=" + productId
						+ ", orderQty=" + orderQty
						+ ", availableStock="
						+ inventory.availableStock()
				);
			}
		}
	}

	private List<OrderProduct> createOrderProducts(
		List<CartItem> cartItems,
		Map<Long, OrderDraftItemRequest> requestByCartItemId
	) {
		return cartItems.stream()
			.flatMap(cartItem -> {
				Long orderQty =
					requestByCartItemId
						.get(cartItem.getId())
						.getOrderQty();

				return OrderProduct
					.createFromCartItem(
						cartItem,
						orderQty
					)
					.stream();
			})
			.toList();
	}
}
