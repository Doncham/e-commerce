package kr.hhplus.be.server.application.payment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.FixturePersist;
import kr.hhplus.be.server.TestFixture;
import kr.hhplus.be.server.application.inventory.InventoryService;
import kr.hhplus.be.server.application.payment.dto.PaymentCancelRequest;
import kr.hhplus.be.server.application.payment.dto.PaymentCancelResponse;
import kr.hhplus.be.server.application.point.PointService;
import kr.hhplus.be.server.domain.address.Address;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.ShippingInfo;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.paymentcancel.PaymentCancel;
import kr.hhplus.be.server.domain.paymentcancel.PaymentCancelFailurePhase;
import kr.hhplus.be.server.domain.paymentcancel.PaymentCancelStatus;
import kr.hhplus.be.server.domain.paymentcancel.exception.PaymentCancelPermanentException;
import kr.hhplus.be.server.domain.product.Product;
import kr.hhplus.be.server.domain.user.User;
import kr.hhplus.be.server.infrastructure.persistence.address.AddressRepository;
import kr.hhplus.be.server.infrastructure.persistence.order.OrderRepository;
import kr.hhplus.be.server.infrastructure.persistence.orderproduct.OrderProductRepository;
import kr.hhplus.be.server.infrastructure.persistence.payment.PaymentRepository;
import kr.hhplus.be.server.infrastructure.persistence.paymentcancel.PaymentCancelRepository;
import kr.hhplus.be.server.infrastructure.persistence.product.ProductRepository;
import kr.hhplus.be.server.infrastructure.persistence.user.UserRepository;

@SpringBootTest
@Transactional
class PaymentServiceIntegrationTest {

	@Autowired
	private PaymentService paymentService;

	@Autowired
	private PaymentCancelRepository paymentCancelRepo;

	@Autowired
	private PaymentRepository paymentRepo;

	@Autowired
	private OrderRepository orderRepo;

	@Autowired
	private OrderProductRepository orderProductRepo;

	@Autowired
	private PointService pointService;

	@Autowired
	private InventoryService inventoryService;
	@Autowired
	private FixturePersist fixturePersist;
	@Autowired
	private UserRepository userRepo;

	@Autowired
	private AddressRepository addressRepo;

	@Autowired
	private ProductRepository productRepo;

	@TestConfiguration
	static class MockDependencyConfig {
		@Bean
		@Primary
		PointService pointService() {
			return Mockito.mock(PointService.class);
		}

		@Bean
		@Primary
		InventoryService inventoryService() {
			return Mockito.mock(InventoryService.class);
		}
	}

	@Test
	@DisplayName("prepareOrGetCancelJob - 같은 fingerprint면 DataIntegrityViolationException 이 발생한다")
	void prepareOrGetCancelJob_throwsDataIntegrityViolationException_whenFingerprintDuplicated() {
		PaidOrderFixture fixture = persistPaidOrderWithCancelableProducts(2);

		PaymentCancelRequest firstRequest = cancelRequest(
			fixture.paymentId(),
			List.of(fixture.orderProductIds().get(0), fixture.orderProductIds().get(1)),
			"cancel-idem-1",
			"단순 변심"
		);

		paymentService.prepareOrGetCancelJob(firstRequest);

		PaymentCancelRequest secondRequest = cancelRequest(
			fixture.paymentId(),
			List.of(fixture.orderProductIds().get(1), fixture.orderProductIds().get(0)),
			"cancel-idem-2",
			"단순 변심"
		);

		assertThatThrownBy(() -> paymentService.prepareOrGetCancelJob(secondRequest))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("completeCancelPayment - PG 완료된 취소건이면 completeCancelPayment 시 연관된 orderProducts와 payment의 상태도 변경된다.")
	void completeCancelPayment_succeeds_whenPgCompleted() {
		PaidOrderFixture fixture = persistPaidOrderWithCancelableProducts(2);

		PaymentCancel paymentCancel = persistProcessingPgCompletedCancel(
			fixture.paymentId(),
			List.of(fixture.orderProductIds().get(0), fixture.orderProductIds().get(1)),
			10000L
		);

		PaymentCancelResponse response = paymentService.completeCancelPayment(paymentCancel.getId());

		PaymentCancel reloadedCancel = paymentCancelRepo.findById(paymentCancel.getId()).orElseThrow();
		List<OrderProduct> linkedProducts = orderProductRepo.findByPaymentCancelId(paymentCancel.getId());
		Payment reloadedPayment = paymentRepo.findById(fixture.paymentId()).orElseThrow();

		assertThat(response).isNotNull();
		assertThat(reloadedCancel.getStatus()).isEqualTo(PaymentCancelStatus.SUCCEEDED);
		assertThat(reloadedCancel.isPgCancelCompleted()).isTrue();
		assertThat(reloadedCancel.getFailurePhase()).isEqualTo(PaymentCancelFailurePhase.AFTER_PG);
		assertThat(linkedProducts).hasSize(2);
		assertThat(linkedProducts).allMatch(op -> !op.isCancelable());
		assertThat(reloadedPayment.canStartCancel()).isFalse();

		verify(pointService, times(1))
			.restorePoint(anyLong(), anyList(), anyLong());
		verify(inventoryService, times(1))
			.restoreInventory(anyList());
	}

	@Test
	@DisplayName("completeCancelPayment - PG 완료 전이면 영구 실패 예외가 발생한다")
	void completeCancelPayment_throwsPermanentException_whenPgNotCompleted() {
		PaidOrderFixture fixture = persistPaidOrderWithCancelableProducts(1);

		PaymentCancel paymentCancel = PaymentCancel.create(
			fixture.paymentId(),
			5000L,
			"cancel-idem-3",
			"단순 변심",
			PaymentCancel.createFingerprint(fixture.paymentId(), List.of(fixture.orderProductIds().get(0))),
			String.valueOf(fixture.orderProductIds().get(0))
		);
		paymentCancel.markProcessing();
		paymentCancel = paymentCancelRepo.saveAndFlush(paymentCancel);

		PaymentCancel finalPaymentCancel = paymentCancel;

		assertThatThrownBy(() -> paymentService.completeCancelPayment(finalPaymentCancel.getId()))
			.isInstanceOf(PaymentCancelPermanentException.class)
			.hasMessageContaining("외부 취소가 완료되지 않은 PaymentCancel");

		PaymentCancel reloaded = paymentCancelRepo.findById(paymentCancel.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(PaymentCancelStatus.PROCESSING);
		assertThat(reloaded.isPgCancelCompleted()).isFalse();
	}

	private PaymentCancelRequest cancelRequest(
		Long paymentId,
		List<Long> orderProductIds,
		String idemKey,
		String reason
	) {
		PaymentCancelRequest request = new PaymentCancelRequest();
		ReflectionTestUtils.setField(request, "paymentId", paymentId);
		ReflectionTestUtils.setField(request, "orderProductIds", orderProductIds);
		ReflectionTestUtils.setField(request, "idemKey", idemKey);
		ReflectionTestUtils.setField(request, "reason", reason);
		return request;
	}

	private PaymentCancel persistProcessingPgCompletedCancel(
		Long paymentId,
		List<Long> targetOrderProductIds,
		Long cancelAmount
	) {
		String snapshot = targetOrderProductIds.stream()
			.sorted()
			.map(String::valueOf)
			.reduce((a, b) -> a + "," + b)
			.orElseThrow();

		PaymentCancel paymentCancel = PaymentCancel.create(
			paymentId,
			cancelAmount,
			"cancel-idem-pg-completed",
			"단순 변심",
			PaymentCancel.createFingerprint(paymentId, targetOrderProductIds),
			snapshot
		);

		paymentCancel.markProcessing();
		paymentCancel.markPgCanceledCompleted(
			"pg-cancel-tx-1",
			LocalDateTime.of(2026, 4, 2, 12, 0)
		);

		return paymentCancelRepo.saveAndFlush(paymentCancel);
	}


	private PaidOrderFixture persistPaidOrderWithCancelableProducts(int productCount) {
		User user = fixturePersist.saveAndFlush(userRepo, TestFixture.user());
		Address address = fixturePersist.saveAndFlush(addressRepo, TestFixture.address(user));
		ShippingInfo shippingInfo = TestFixture.shippingFrom(address);

		List<OrderProduct> items = IntStream.range(0, productCount)
			.mapToObj(i -> {
				Product product = fixturePersist.saveAndFlush(
					productRepo,
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
			0L,        // couponDiscount
			"test memo",
			0L         // pointUsed
		);

		order = fixturePersist.saveAndFlush(orderRepo, order);

		Payment payment = TestFixture.successPayment(order, order.getPaymentAmount());

		// 취소 가능 상태를 맞추기 위해 주문도 paid 처리
		order.paid();

		payment = fixturePersist.saveAndFlush(paymentRepo, payment);

		List<Long> orderProductIds = orderProductRepo.findByOrderId(order.getId()).stream()
			.map(OrderProduct::getId)
			.toList();

		return new PaidOrderFixture(
			payment.getId(),
			order.getId(),
			orderProductIds
		);
	}

	private record PaidOrderFixture(
		Long paymentId,
		Long orderId,
		List<Long> orderProductIds
	) {
	}
}