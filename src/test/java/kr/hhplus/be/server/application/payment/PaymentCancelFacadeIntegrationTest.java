package kr.hhplus.be.server.application.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;

import kr.hhplus.be.server.FixturePersist;
import kr.hhplus.be.server.TestFixture;
import kr.hhplus.be.server.application.inventory.InventoryService;
import kr.hhplus.be.server.application.payment.dto.PaymentCancelRequest;
import kr.hhplus.be.server.application.payment.dto.PaymentCancelResponse;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayCancelResponse;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayRouter;
import kr.hhplus.be.server.application.point.PointService;
import kr.hhplus.be.server.domain.address.Address;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.ShippingInfo;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentGatewayPort;
import kr.hhplus.be.server.domain.payment.PaymentGatewayStatus;
import kr.hhplus.be.server.domain.payment.dto.PaymentCancelJob;
import kr.hhplus.be.server.domain.paymentcancel.PaymentCancel;
import kr.hhplus.be.server.domain.paymentcancel.PaymentCancelStatus;
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
class PaymentCancelFacadeIntegrationTest {

	@Autowired
	private PaymentCancelFacade paymentCancelFacade;

	@Autowired
	private PaymentService paymentService;

	@Autowired
	private PaymentCancelRepository paymentCancelRepo;

	@Autowired
	private FixturePersist fixturePersist;
	@Autowired
	private PaymentRepository paymentRepo;
	@Autowired
	private UserRepository userRepo;

	@Autowired
	private AddressRepository addressRepo;

	@Autowired
	private OrderRepository orderRepo;

	@Autowired
	private OrderProductRepository orderProductRepo;
	@Autowired
	private ProductRepository productRepo;

	@Autowired
	private PaymentTestFixtureBuilder paymentTestFixtureBuilder;


	@MockitoBean
	private PaymentGatewayRouter paymentGatewayRouter;

	@MockitoBean
	private PaymentGatewayPort paymentGatewayPort;

	@MockitoBean
	private PointService pointService;

	@MockitoBean
	private InventoryService inventoryService;

	@AfterEach
	void cleanup() {
		orderProductRepo.deleteAllInBatch();
		paymentCancelRepo.deleteAllInBatch();
		paymentRepo.deleteAllInBatch();
		orderRepo.deleteAllInBatch();
		addressRepo.deleteAllInBatch();
		productRepo.deleteAllInBatch();
		userRepo.deleteAllInBatch();
	}

	@Test
	@DisplayName("cancelPayment - duplicate fingerprint면 기존 PaymentCancel을 복구해서 정상 취소를 완료한다")
	void cancelPayment_recoversExistingPaymentCancel_whenFingerprintDuplicated() {
		PaymentTestFixtureBuilder.PaidOrderFixture fixture = paymentTestFixtureBuilder.persistPaidOrderWithCancelableProducts(2);

		PaymentCancelRequest firstRequest = cancelRequest(
			fixture.paymentId(),
			List.of(fixture.orderProductIds().get(0), fixture.orderProductIds().get(1)),
			"cancel-idem-1",
			"단순 변심"
		);

		// 첫 요청: REQUESTED PaymentCancel 생성
		PaymentCancelJob firstJob = paymentService.prepareOrGetCancelJob(firstRequest);

		PaymentGatewayCancelResponse pgResponse = Mockito.mock(PaymentGatewayCancelResponse.class);
		when(paymentGatewayRouter.route(any())).thenReturn(paymentGatewayPort);
		when(paymentGatewayPort.cancel(any())).thenReturn(pgResponse);
		when(pgResponse.getStatus()).thenReturn(PaymentGatewayStatus.SUCCESS);
		when(pgResponse.getPgCancelTransactionId()).thenReturn("pg-cancel-tx-1");
		when(pgResponse.getCanceledAt()).thenReturn(LocalDateTime.of(2026, 4, 3, 12, 0));

		PaymentCancelRequest duplicateRequest = cancelRequest(
			fixture.paymentId(),
			List.of(fixture.orderProductIds().get(1), fixture.orderProductIds().get(0)), // 순서만 다름
			"cancel-idem-2",
			"단순 변심"
		);

		PaymentCancelResponse response = paymentCancelFacade.cancelPayment(duplicateRequest);

		String fingerprint = paymentService.getFingerPrint(duplicateRequest);
		PaymentCancel reloaded = paymentCancelRepo.findByCancelFingerPrint(fingerprint)
			.orElseThrow();

		assertThat(response).isNotNull();
		assertThat(reloaded.getId()).isEqualTo(firstJob.getPaymentCancelId());
		assertThat(reloaded.getStatus()).isEqualTo(PaymentCancelStatus.SUCCEEDED);
		assertThat(reloaded.isPgCancelCompleted()).isTrue();
		assertThat(reloaded.getPgCancellationId()).isEqualTo("pg-cancel-tx-1");

		verify(paymentGatewayRouter).route(any());
		verify(paymentGatewayPort).cancel(any());
		verify(pointService).restorePoint(anyLong(), anyList(), anyLong());
		verify(inventoryService).restoreInventory(anyList());
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
		String idempotencyKey = UUID.randomUUID().toString();
		Payment payment = TestFixture.successPayment(order, order.getPaymentAmount(),idempotencyKey);

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