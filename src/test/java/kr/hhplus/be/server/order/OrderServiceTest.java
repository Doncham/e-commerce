package kr.hhplus.be.server.order;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import kr.hhplus.be.server.domain.address.Address;
import kr.hhplus.be.server.application.address.AddressPort;
import kr.hhplus.be.server.application.order.OrderService;
import kr.hhplus.be.server.domain.cartItem.CartItem;
import kr.hhplus.be.server.application.cartItem.CartItemPort;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.inventory.Inventory;
import kr.hhplus.be.server.domain.inventory.exception.InSufficientStockException;
import kr.hhplus.be.server.application.inventory.InventoryPort;
import kr.hhplus.be.server.api.order.request.OrderDraftCreateRequest;
import kr.hhplus.be.server.api.order.response.OrderDraftCreateResponse;
import kr.hhplus.be.server.application.order.OrderPort;
import kr.hhplus.be.server.domain.product.Product;
import kr.hhplus.be.server.domain.user.User;
import kr.hhplus.be.server.domain.user.UserPort;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
	@InjectMocks
	private OrderService orderService;
	@Mock
	private OrderPort orderPort;
	@Mock
	private AddressPort addressPort;
	@Mock
	private UserPort userPort;
	@Mock
	private CartItemPort cartItemPort;
	@Mock
	private InventoryPort inventoryPort;

	@Test
	// 주문 생성 성공 테스트
	void givenValidCartItemIdAndAddressId_whenCreateOrder_thenReturnOrderWithStatusCreated(){
		// given
		Long cartId = 1L;
		Long addressId = 1L;
		Long userId = 15L;
		Long couponId = 30L;
		OrderDraftCreateRequest request = OrderDraftCreateRequest.builder()
			.cartId(cartId)
			.addressId(addressId)
			.couponId(couponId)
			// JWT 대체 예정
			.userId(userId)
			.memo("빠른 배송 부탁드립니다.")
			.build();

		when(addressPort.loadAddress(addressId)).thenReturn(
			Address.builder()
				.address("Incheon, Korea, 어쩌고 아파트 몇동 몇호")
				.phoneNumber("010-1234-5678")
				.zipcode("12345")
				.receiver("홍길동")
				.build());


		when(userPort.loadUser(userId)).thenReturn(
			testUser(userId)
		);


		Product product1 = testProduct(200L, "상품1", 1500L);
		Product product2 = testProduct(201L, "상품2", 1000L);

		CartItem cartItem1 = testCartItem(product1, 2);
		CartItem cartItem2 = testCartItem(product2, 1);

		when(cartItemPort.findByCartIdWithProduct(cartId))
			.thenReturn(List.of(
				cartItem1,
				cartItem2
			));

		// inventory List 만들어서 반환
		Inventory inventory1 = testInventory(product1, 10L, 0L);
		Inventory inventory2 = testInventory(product2, 5L, 0L);
		when(inventoryPort.findByProductIdIn(anyList()))
			.thenReturn(List.of(
				inventory1,
				inventory2
			));

		// when
		OrderDraftCreateResponse order = orderService.createOrder(request);
		// then
		verify(orderPort).save(any(Order.class));
		assertNotNull(order);
		assertEquals(order.getOrderStatus(), "CREATED");
		assertEquals(order.getItemTotal(), 4000L);
		assertEquals(2, order.getItems().size());
		assertEquals(1500L, order.getItems().get(0).getUnitPrice());
		assertEquals(2, order.getItems().get(0).getQty());
		assertEquals(1000L, order.getItems().get(1).getUnitPrice());
		assertEquals(1, order.getItems().get(1).getQty());
		assertEquals(2, inventory1.getReserved());
		assertEquals(1, inventory2.getReserved());
	}

	@Test
	// 주문 생성 실패 테스트 - 재고 부족
	void givenInsufficientStock_whenCreateOrder_thenThrowInSufficientStockException() {
		// given
		Long cartId = 1L;
		Long addressId = 1L;
		Long userId = 15L;
		Long couponId = 30L;
		OrderDraftCreateRequest request = OrderDraftCreateRequest.builder()
			.cartId(cartId)
			.addressId(addressId)
			.couponId(couponId)
			// JWT 대체 예정
			.userId(userId)
			.memo("빠른 배송 부탁드립니다.")
			.build();

		when(addressPort.loadAddress(addressId)).thenReturn(
			Address.builder()
				.address("Incheon, Korea, 어쩌고 아파트 몇동 몇호")
				.phoneNumber("010-1234-5678")
				.zipcode("12345")
				.receiver("홍길동")
				.build());

		when(userPort.loadUser(userId)).thenReturn(
			testUser(userId)
		);


		Product product1 = testProduct(200L, "상품1", 1500L);
		Product product2 = testProduct(201L, "상품2", 1000L);

		CartItem cartItem1 = testCartItem(product1, 2);
		CartItem cartItem2 = testCartItem(product2, 1);

		when(cartItemPort.findByCartIdWithProduct(cartId))
			.thenReturn(List.of(
				cartItem1,
				cartItem2
			));

		// inventory List 만들어서 반환
		Inventory inventory1 = testInventory(product1, 10L, 10L);
		Inventory inventory2 = testInventory(product2, 5L, 0L);
		when(inventoryPort.findByProductIdIn(anyList()))
			.thenReturn(List.of(
				inventory1,
				inventory2
			));

		// when + then
		assertThrows(InSufficientStockException.class, () -> {
			orderService.createOrder(request);
		});
		verify(orderPort, never()).save(any(Order.class));

	}

	private Address testAddress() {
		return Address.builder()
			.address("Incheon, Korea, 어쩌고 아파트 몇동 몇호")
			.phoneNumber("010-1234-5678")
			.zipcode("12345")
			.receiver("홍길동")
			.build();
	}

	private Product testProduct(long id, String name, long price) {
		Product p = Product.createProduct(name, name + " 설명", price);
		ReflectionTestUtils.setField(p, "id", id);
		return p;
	}

	private CartItem testCartItem(Product product, int qty) {
		return CartItem.builder()
			.product(product)
			.qty(qty)
			.build();
	}

	private Inventory testInventory(Product product, long stock, long reserved) {
		return Inventory.of(product, stock, reserved);
	}
	private User testUser(long id) {
		User u = new User();
		ReflectionTestUtils.setField(u, "id", id);
		return u;
	}


}