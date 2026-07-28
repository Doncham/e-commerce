package kr.hhplus.be.server.application.payment;

import java.util.List;
import java.util.stream.IntStream;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.TestFixture;
import kr.hhplus.be.server.domain.address.Address;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.ShippingInfo;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.product.Product;
import kr.hhplus.be.server.domain.user.User;
import kr.hhplus.be.server.infrastructure.persistence.address.AddressRepository;
import kr.hhplus.be.server.infrastructure.persistence.order.OrderRepository;
import kr.hhplus.be.server.infrastructure.persistence.orderproduct.OrderProductRepository;
import kr.hhplus.be.server.infrastructure.persistence.payment.PaymentRepository;
import kr.hhplus.be.server.infrastructure.persistence.product.ProductRepository;
import kr.hhplus.be.server.infrastructure.persistence.user.UserRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PaymentTestFixtureBuilder {

	private final UserRepository userRepo;
	private final AddressRepository addressRepo;
	private final ProductRepository productRepo;
	private final OrderRepository orderRepo;
	private final OrderProductRepository orderProductRepo;
	private final PaymentRepository paymentRepo;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public PaidOrderFixture persistPaidOrderWithCancelableProducts(int productCount) {
		User user = userRepo.save(TestFixture.user());
		Address address = addressRepo.save(TestFixture.address(user));
		ShippingInfo shippingInfo = TestFixture.shippingFrom(address);

		List<OrderProduct> items = IntStream.range(0, productCount)
			.mapToObj(i -> {
				Product product = productRepo.save(
					TestFixture.product("product-" + i, 5_000L)
				);

				return TestFixture.orderProduct(
					product.getId(),
					product.getName(),
					product.getPrice()
				);
			})
			.toList();

		Order order = TestFixture.createdOrder(
			user,
			shippingInfo,
			TestFixture.idemKey(),
			items,
			0L,
			"test memo",
			0L
		);

		order = orderRepo.saveAndFlush(order);

		// createdOrder -> completeOrderDraft 로 payAmount/orderProducts 가 세팅된 상태여야 함
		order.paid();
		orderRepo.flush(); // 같은 Tx 안 managed 상태 변경을 명시적으로 flush

		Payment payment = TestFixture.successPayment(order, order.getPaymentAmount());
		payment = paymentRepo.saveAndFlush(payment);

		List<Long> orderProductIds = orderProductRepo.findByOrderId(order.getId()).stream()
			.map(OrderProduct::getId)
			.toList();

		return new PaidOrderFixture(
			user.getId(),
			order.getId(),
			payment.getId(),
			orderProductIds
		);
	}

	public record PaidOrderFixture(
		Long userId,
		Long orderId,
		Long paymentId,
		List<Long> orderProductIds
	) {
	}
}