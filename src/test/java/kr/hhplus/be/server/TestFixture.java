package kr.hhplus.be.server;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import kr.hhplus.be.server.domain.address.Address;
import kr.hhplus.be.server.domain.cart.Cart;
import kr.hhplus.be.server.domain.cartItem.CartItem;
import kr.hhplus.be.server.domain.inventory.Inventory;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.ShippingInfo;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.product.Product;
import kr.hhplus.be.server.domain.user.User;

/**
 * "엔티티 생성" 전용 Fixture.
 * - save는 하지 않는다. (저장은 FixturePersist 같은 유틸로)
 * - 테스트에서 필요한 최소 기본값을 제공한다.
 */
public final class TestFixture {

	private TestFixture() {}

	private static final AtomicLong SEQ = new AtomicLong(1);

	// ===== 공용 값 생성기 =====
	public static String idemKey() {
		return "idem-" + UUID.randomUUID();
	}

	public static String pgTxId() {
		return "pg-" + UUID.randomUUID();
	}

	public static LocalDateTime now() {
		return LocalDateTime.now();
	}

	private static long next() {
		return SEQ.getAndIncrement();
	}

	// ===== User =====
	public static User user() {
		long n = next();
		return User.create("user" + n + "@test.com", "user" + n);
	}

	public static User user(String email, String name) {
		return User.create(email, name);
	}

	// ===== Product =====
	public static Product product() {
		long n = next();
		return Product.createProduct("product-" + n, "desc-" + n, 10_000L);
	}

	public static Product product(String name, Long price) {
		return Product.createProduct(name, "desc-" + name, price);
	}

	public static Product product(String name, String description, Long price) {
		return Product.createProduct(name, description, price);
	}

	// ===== Inventory =====
	/**
	 * 재고 기본값: stock=100, reserved=0
	 */
	public static Inventory inventory(Product product) {
		return Inventory.of(product, 100L);
	}

	public static Inventory inventory(Product product, Long stock) {
		return Inventory.of(product, stock);
	}

	// ===== Address =====
	public static Address address(User user) {
		long n = next();
		return Address.builder()
			.user(user)
			.receiver("receiver-" + n)
			.phoneNumber("010-0000-" + String.format("%04d", n % 10000))
			.zipcode("12345")
			.address("Seoul Road " + n)
			.memo("memo-" + n)
			.isDefault(true)
			.build();
	}

	public static Address address(User user, boolean isDefault) {
		Address addr = address(user);
		// builder로 이미 만들어졌으니 isDefault만 바꾸고 싶으면 Address 엔티티에 setter가 없어서
		// 여기선 새로 생성하는 방식으로 제공
		return Address.builder()
			.user(user)
			.receiver(addr.getReceiver())
			.phoneNumber(addr.getPhoneNumber())
			.zipcode(addr.getZipcode())
			.address(addr.getAddress())
			.memo(addr.getMemo())
			.isDefault(isDefault)
			.build();
	}

	// ===== ShippingInfo =====

	public static ShippingInfo shippingFrom(Address address) {
		return new ShippingInfo(address);
	}

	// ===== OrderProduct =====
	public static OrderProduct orderProduct(Product product, long qty) {
		return OrderProduct.create(
			product.getId(),            // 주의: product가 아직 저장 전이면 id=null
			product.getName(),
			product.getPrice(),
			qty
		);
	}

	/**
	 * product 저장 전이라 id가 null일 수 있음.
	 * 통합테스트에서는 보통 Product를 먼저 저장 후 그 id를 넣는게 맞다.
	 */
	public static OrderProduct orderProduct(Long productId, String productNameSnap, Long unitPrice, long qty) {
		return OrderProduct.create(productId, productNameSnap, unitPrice, qty);
	}

	// ===== Order =====
	public static Order draftOrder(User user, ShippingInfo shippingInfo) {
		return Order.createDraft(user, shippingInfo, idemKey());
	}

	public static Order draftOrder(User user, ShippingInfo shippingInfo, String idemKey) {
		return Order.createDraft(user, shippingInfo, idemKey);
	}

	public static Order createdOrder(
		User user,
		ShippingInfo shippingInfo,
		List<OrderProduct> items,
		Long couponId,
		Long couponDiscount,
		String memo,
		Long pointUsed
	) {
		Order order = Order.createDraft(user, shippingInfo, idemKey());
		order.completeOrderDraft(items, couponId, couponDiscount, memo, pointUsed);
		return order;
	}

	public static Order createdOrder(
		User user,
		ShippingInfo shippingInfo,
		String orderIdemKey,
		List<OrderProduct> items,
		Long couponId,
		Long couponDiscount,
		String memo,
		Long pointUsed
	) {
		Order order = Order.createDraft(user, shippingInfo, orderIdemKey);
		order.completeOrderDraft(items, couponId, couponDiscount, memo, pointUsed);
		return order;
	}

	// ==== cartItem ====
	public static CartItem cartItem(
		Cart cart,
		Product p,
		int qty
	) {
		return CartItem.of(cart, p, qty);
	}

	// ==== cart ====
	public static Cart cart(
		User user
	) {
		return Cart.of(user);
	}


	// ===== Payment =====
	public static Payment requestedPayment(Order order, Long amount) {
		return Payment.createPayment(order, idemKey(), amount);
	}

	public static Payment requestedPayment(Order order, String paymentIdemKey, Long amount) {
		return Payment.createPayment(order, paymentIdemKey, amount);
	}

	public static Payment successPayment(Order order, Long amount) {
		Payment p = Payment.createPayment(order, idemKey(), amount);
		p.paymentSuccess(pgTxId(), now());
		return p;
	}

	public static Payment failedPayment(Order order, Long amount, String reason) {
		Payment p = Payment.createPayment(order, idemKey(), amount);
		p.paymentFailed(pgTxId(), reason);
		return p;
	}
}
