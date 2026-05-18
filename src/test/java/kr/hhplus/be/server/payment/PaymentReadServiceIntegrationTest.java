package kr.hhplus.be.server.payment;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.FixturePersist;
import kr.hhplus.be.server.TestFixture;
import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;
import kr.hhplus.be.server.application.payment.PaymentService;
import kr.hhplus.be.server.application.payment.dto.PaymentAttempt;
import kr.hhplus.be.server.application.payment.dto.PaymentCancelRequest;
import kr.hhplus.be.server.application.paymentCancel.PaymentCancelStatusService;
import kr.hhplus.be.server.domain.address.Address;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.domain.orderproduct.OrderProductStatus;
import kr.hhplus.be.server.domain.payment.CancelType;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentGatewayStatus;
import kr.hhplus.be.server.domain.payment.PaymentStatus;
import kr.hhplus.be.server.domain.payment.dto.PaymentCancelJob;
import kr.hhplus.be.server.domain.payment.dto.PaymentDetailItemResponse;
import kr.hhplus.be.server.domain.payment.dto.PaymentDetailResponse;
import kr.hhplus.be.server.domain.user.User;
import kr.hhplus.be.server.infrastructure.persistence.address.AddressRepository;
import kr.hhplus.be.server.infrastructure.persistence.order.OrderRepository;
import kr.hhplus.be.server.infrastructure.persistence.orderproduct.OrderProductRepository;
import kr.hhplus.be.server.infrastructure.persistence.payment.PaymentRepository;
import kr.hhplus.be.server.infrastructure.persistence.paymentcancel.PaymentCancelRepository;
import kr.hhplus.be.server.infrastructure.persistence.user.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
public class PaymentReadServiceIntegrationTest {
	@Autowired
	private PaymentService paymentService;
	@Autowired
	private UserRepository userRepo;
	@Autowired
	private OrderProductRepository orderProductRepo;
	@Autowired
	private OrderRepository orderRepo;
	@Autowired
	private PaymentRepository paymentRepo;
	@Autowired
	private PaymentCancelRepository paymentCancelRepo;
	@Autowired
	private FixturePersist persist;
	@Autowired
	private AddressRepository addressRepo;
	@Autowired
	private PaymentCancelStatusService paymentCancelStatusService;

	@Test
	@Transactional
	// 결제 부분 취소 로직 만들어서 상태 변경 체크
	void givenValidPaymentId_whenReadPayment_thenReturnProperDto() {
		// given
		User user = persist.save(userRepo, User.create("abc@naver.com", "onion"));
		Address addr = persist.saveAndFlush(addressRepo, TestFixture.address(user));

		OrderProduct op1 = TestFixture.orderProduct(1L, "모자", 5000L);
		OrderProduct op2 = TestFixture.orderProduct(2L, "신발", 20000L);

		Order order = persist.save(orderRepo,
			TestFixture.createdOrder(user, TestFixture.shippingFrom(addr), "orderIdem-123", List.of(op1, op2), 0L,
				"빠르게!", 0L));

		op1.initOrder(order);
		op2.initOrder(order);
		persist.save(orderProductRepo, op1);
		persist.save(orderProductRepo, op2);


		//Payment payment = persist.saveAndFlush(paymentRepo, TestFixture.successPayment(order, 25000L));
		PaymentAttempt paymentAttempt = paymentService.preparePayment(order.getId());
		paymentService.completePayment(paymentAttempt.getPaymentId(), PaymentGatewayResponse.of("pg-tx-id",
			PaymentGatewayStatus.SUCCESS, paymentAttempt.getAmount(), null, null));

		PaymentCancelRequest request = PaymentCancelRequest.builder()
			.paymentId(paymentAttempt.getPaymentId())
			.idemKey("cancel-idem-key")
			.reason("단순 변심")
			.orderProductIds(List.of(op1.getId()))
			.userId(user.getId())
			.cancelType(CancelType.PARTIAL)
			.build();
		PaymentCancelJob paymentCancelJob = paymentService.prepareOrGetCancelJob(request);
		Long paymentCancelId = paymentCancelJob.getPaymentCancelId();
		paymentCancelStatusService.markProcessing(paymentCancelId);
		paymentCancelStatusService.markPgCanceledCompleted(paymentCancelId, "pg-cancel-123", LocalDateTime.now());
		paymentService.completeCancelPayment(paymentCancelId);
		Long paymentId = paymentAttempt.getPaymentId();

		// when
		PaymentDetailResponse paymentDetail = paymentService.getPaymentDetail(paymentId);
		List<PaymentDetailItemResponse> orderProducts = paymentDetail.getOrderProductDetails();

		// then
		Assertions.assertEquals(paymentAttempt.getPaymentId(), paymentDetail.getPaymentId());
		Assertions.assertEquals(PaymentStatus.PARTIAL_CANCELED, paymentDetail.getPaymentStatus());
		Assertions.assertEquals(order.getId(), paymentDetail.getOrderId());
		Assertions.assertEquals(OrderStatus.PARTIAL_CANCELED, paymentDetail.getOrderStatus());
		Assertions.assertEquals(20000L, paymentDetail.getCancelableAmount());
		Assertions.assertEquals(5000L, paymentDetail.getCancelAmountTotal());
		Assertions.assertEquals( 25000L, paymentDetail.getPaidAmount());
		Assertions.assertEquals(2, orderProducts.size());
		Assertions.assertEquals(op1.getProductId(), orderProducts.get(0).getProductId());
		Assertions.assertEquals(OrderProductStatus.CANCELED, orderProducts.get(0).getOrderProductStatus());
		Assertions.assertEquals("단순 변심", orderProducts.get(0).getCancelReason());



	}
}
