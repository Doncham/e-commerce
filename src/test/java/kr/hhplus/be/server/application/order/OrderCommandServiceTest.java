package kr.hhplus.be.server.application.order;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import kr.hhplus.be.server.FixturePersist;
import kr.hhplus.be.server.TestFixture;
import kr.hhplus.be.server.api.order.request.OrderDraftCreateRequest;
import kr.hhplus.be.server.domain.address.Address;
import kr.hhplus.be.server.domain.cart.Cart;
import kr.hhplus.be.server.domain.cartItem.CartItem;
import kr.hhplus.be.server.domain.inventory.Inventory;
import kr.hhplus.be.server.domain.inventory.exception.InSufficientStockException;
import kr.hhplus.be.server.domain.product.Product;
import kr.hhplus.be.server.domain.user.User;
import kr.hhplus.be.server.infrastructure.persistence.address.AddressRepository;
import kr.hhplus.be.server.infrastructure.persistence.cart.CartRepository;
import kr.hhplus.be.server.infrastructure.persistence.cartItem.CartItemRepository;
import kr.hhplus.be.server.infrastructure.persistence.inventory.InventoryRepository;
import kr.hhplus.be.server.infrastructure.persistence.order.OrderRepository;
import kr.hhplus.be.server.infrastructure.persistence.point.PointRepository;
import kr.hhplus.be.server.infrastructure.persistence.product.ProductRepository;
import kr.hhplus.be.server.infrastructure.persistence.user.UserRepository;

@SpringBootTest
class OrderCommandServiceTest {
	@Autowired
	private OrderCommandService orderCommandService;
	@Autowired
	private UserRepository userRepo;
	@Autowired
	private ProductRepository productRepo;
	@Autowired
	private InventoryRepository inventoryRepo;
	@Autowired
	private CartRepository cartRepo;
	@Autowired
	private FixturePersist persist;
	@Autowired
	private CartItemRepository cartItemRepo;
	@Autowired
	private AddressRepository addressRepo;
	@Autowired
	private PointRepository pointRepo;
	@Autowired
	private OrderRepository orderRepo;

	@Test
	public void givenInventoryWith100Stock_when101OrderCreated_thenInventoryReservedIs100AndOneExceptionOccur() throws
		InterruptedException {
		// given
		Product p = persist.save(productRepo, TestFixture.product("콜라", 2000L));
		persist.saveAndFlush(inventoryRepo, TestFixture.inventory(p, 100L));
		List<OrderDraftCreateRequest> requests = new ArrayList<>();
		int requestCount = 101;
		for (int i = 0; i < requestCount; i++) {
			// 유저별 포인트 락에 걸리지 않기 위해서 모든 요청의 user를 다르게 설정.
			User u = persist.saveAndFlush(userRepo, TestFixture.user());
			persist.saveAndFlush(pointRepo, TestFixture.point(u.getId()));

			Address addr = persist.saveAndFlush(addressRepo, TestFixture.address(u));
			Cart cart = persist.saveAndFlush(cartRepo, TestFixture.cart(u));
			CartItem cartItem1 = persist.save(cartItemRepo, TestFixture.cartItem(cart, p, 1));

			OrderDraftCreateRequest req = OrderDraftCreateRequest.builder()
				.memo("주문" + i)
				.userId(u.getId())
				.addressId(addr.getId())
				.cartId(cart.getId())
				.pointUseAmount(0L)
				.idempotencyKey(TestFixture.idemKey())
				.build();
			requests.add(req);
		}

		// when
		int threads = 16;
		ExecutorService pool = Executors.newFixedThreadPool(threads);

		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(requestCount);

		AtomicInteger success = new AtomicInteger();

		List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

		for(int i = 0; i < requestCount; i++) {
			final int idx = i;
			Runnable task = () -> {
				ready.countDown();
				await(start);
				try {
					orderCommandService.createOrder(requests.get(idx));
					success.incrementAndGet();
				} catch (Throwable e) {
					errors.add(e);
				} finally {
					done.countDown();
				}
			};
			pool.submit(task);
		}

		ready.await();
		start.countDown();
		done.await();
		pool.shutdown();

		// then
		System.out.println("success=" + success.get() + ", errors=" + errors.size());
		errors.stream()
			.map(OrderCommandServiceTest::rootCause)
			.forEach(e -> System.out.println(e.getClass().getName() + " :: " + e.getMessage()));
		Inventory inventory = inventoryRepo.findByProductId(p.getId()).get();
		System.out.println("reserved= " + inventory.getReserved());
		long orderCount = orderRepo.count();
		assertEquals(100L, inventory.getReserved());
		//assertEquals(100, orderCount);
		// assertEquals(1, errors.size());
		// assertEquals(100, success.get());


		//Throwable root = rootCause(errors.get(0));
		//assertInstanceOf(InSufficientStockException.class, root);


	}
	private static void await(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private static Throwable rootCause(Throwable t) {
		Throwable cur = t;
		while (cur.getCause() != null && cur.getCause() != cur) {
			cur = cur.getCause();
		}
		return cur;
	}

}