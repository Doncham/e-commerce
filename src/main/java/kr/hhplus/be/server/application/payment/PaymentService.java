package kr.hhplus.be.server.application.payment;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.api.payment.request.PayResponse;
import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;

import kr.hhplus.be.server.application.payment.dto.CancelCommand;
import kr.hhplus.be.server.application.payment.dto.PaymentAttempt;
import kr.hhplus.be.server.application.payment.dto.PaymentCancelResponse;
import kr.hhplus.be.server.application.payment.dto.PaymentFullCancelRequest;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayCancelResponse;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayType;
import kr.hhplus.be.server.domain.coupon.exception.CouponExpiredException;
import kr.hhplus.be.server.domain.coupon.exception.InsufficientCouponStockException;
import kr.hhplus.be.server.domain.coupon.exception.NotFoundCoupon;
import kr.hhplus.be.server.domain.coupon.exception.UserCouponLimitExceededException;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.exception.OrderAlreadyPaidOrderException;
import kr.hhplus.be.server.domain.order.exception.OrderNotFoundException;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.domain.orderproduct.OrderProductStatus;
import kr.hhplus.be.server.domain.payment.CancelType;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentGatewayStatus;
import kr.hhplus.be.server.domain.payment.dto.PaymentDetailResponse;
import kr.hhplus.be.server.domain.payment.exception.PaymentNotFoundException;
import kr.hhplus.be.server.domain.paymentcancel.PaymentCancel;
import kr.hhplus.be.server.infrastructure.persistence.order.OrderRepository;
import kr.hhplus.be.server.infrastructure.persistence.orderproduct.OrderProductRepository;
import kr.hhplus.be.server.infrastructure.persistence.payment.PaymentRepository;
import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.infrastructure.persistence.paymentcancel.PaymentCancelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
	private final PaymentRepository paymentRepo;
	private final OrderRepository orderRepo;
	private final OrderProductRepository orderProductRepo;
	private final PaymentCancelRepository paymentCancelRepo;
	private final PaymentReservationProcessor reservationProcessor;
	private final PaymentOutboxPublisher outboxPublisher;
	private final Clock clock;

	@Transactional
	public PaymentAttempt preparePayment(Long orderId, String idemKey) {
		Order order = orderRepo.findByIdForUpdate(orderId)
			.orElseThrow(() -> new OrderNotFoundException(ErrorCode.NOT_FOUND_ORDER, orderId));

		if(order.isPaid()) {
			// 이미 성공 결제면 예외
			throw new OrderAlreadyPaidOrderException(ErrorCode.ALREADY_PAID_ORDER, orderId);
		}

		Payment pending = Payment.createPayment(order, idemKey, order.getPayAmount(), PaymentGatewayType.TOSS);
		pending = paymentRepo.saveAndFlush(pending); // 유니크 충돌 빠르게 확정

		return PaymentAttempt.of(orderId, pending.getId(), pending.getAmount(), pending.getIdempotencyKey());
	}

	@Transactional
	@Retryable(
		retryFor = {
			org.springframework.dao.CannotAcquireLockException.class,
			org.springframework.dao.PessimisticLockingFailureException.class,
		},
		noRetryFor = {
			InsufficientCouponStockException.class,
			CouponExpiredException.class,
			UserCouponLimitExceededException.class,
			NotFoundCoupon.class
		},
		maxAttempts = 3,
		backoff = @Backoff(delay = 50, multiplier = 2.0, random = true),
		exceptionExpression = "@lockRetryPolicy.isMySqlLockWaitTimeout(#root)"
	)
	public PayResponse completePayment(Long paymentId, PaymentGatewayResponse pgResp, PaymentGatewayType gatewayType) {
		Payment payment = paymentRepo.findByIdForUpdate(paymentId)
			.orElseThrow(() ->
				new PaymentNotFoundException(ErrorCode.NOT_FOUND_PAYMENT, paymentId));
		// order 상태를 변경할거니까 락을 걸어서 조회하는건가?
		Long orderId = payment.getOrder().getId();
		Order order = orderRepo.findByIdForUpdate(orderId)
			.orElseThrow(() -> new OrderNotFoundException(ErrorCode.NOT_FOUND_ORDER, orderId));;

		// 이미 처리된 결제면 멱등 반환
		if(payment.isFinalized()) {
			return PayResponse.of(order, payment);
		}
		if (pgResp.getStatus() != PaymentGatewayStatus.SUCCESS) {
			return failPayment(pgResp, payment, order, "PG_FAILED");
		}
		if (pgResp.getPaidAmount() == null || !pgResp.getPaidAmount().equals(order.getPayAmount())) {
			return failPayment(pgResp, payment, order, "PAY_AMOUNT_MISMATCH");
		}

		return succeedPayment(pgResp, payment, order, clock);
	}
	private PayResponse succeedPayment(PaymentGatewayResponse pgResp, Payment payment, Order order, Clock clock) {
		LocalDateTime paidAt = LocalDateTime.now(clock);
		payment.paymentSuccess(pgResp.getPgTransactionId(), paidAt);

		reservationProcessor.confirm(order);
		order.paid();
		outboxPublisher.publishPaymentSuccess(order, pgResp.getPgTransactionId(), paidAt);

		return PayResponse.of(order, payment);
	}

	private PayResponse failPayment(PaymentGatewayResponse pgResp, Payment payment, Order order, String reason) {
		payment.paymentFailed(pgResp.getPgTransactionId(), reason);
		order.failed();
		reservationProcessor.release(order, reason);

		return PayResponse.of(order, payment);
	}

	@Transactional(readOnly = true)
	public PaymentDetailResponse getPaymentDetail(Long paymentId) {
		// 나중에 JWT에 있는 userId와 payment.user.id 검증 필요
		Payment payment = paymentRepo.findByIdForPaymentDetailResponse(paymentId)
			.orElseThrow(() ->
				new PaymentNotFoundException(ErrorCode.NOT_FOUND_PAYMENT, paymentId));
		return PaymentDetailResponse.create(payment);
	}



	@Transactional
	public CancelCommand prepareFullCancel(PaymentFullCancelRequest request) {
		// payment_id, idemKey에 인덱스를 걸어서 유니크 제약 조건 위반 유발
		Long paymentId = request.getPaymentId();
		Payment payment = getPaymentAndCheckStatus(paymentId);

		Long orderId = payment.getOrder().getId();
		checkOrderStatus(orderId);

		List<OrderProduct> cancelableOrderProducts = orderProductRepo.findCancelableByOrderId(orderId, OrderProductStatus.ORDERED);
		long cancelAmountTotal = getCancelAmountTotalAndVerify(cancelableOrderProducts);

		List<Long> cancelableOrderProductIds = extractCancelableIds(cancelableOrderProducts, orderId);

		createCancelRequestWithFingerprint(request, cancelableOrderProductIds, paymentId, cancelAmountTotal);

		return CancelCommand.builder()
			.paymentId(paymentId)
			.orderId(orderId)
			.reason(request.getReason())
			.cancelType(CancelType.FULL)
			.cancelAmount(cancelAmountTotal)
			.idempotencyKey(request.getIdemKey())
			.targetOrderProducts(cancelableOrderProductIds)
			.pgTransactionId(payment.getPgTransactionId())
			.gatewayType(payment.getGatewayType())
			.build();
	}

	private static long getCancelAmountTotalAndVerify(List<OrderProduct> cancelableOrderProducts) {
		long cancelAmountTotal = cancelableOrderProducts.stream()
			.mapToLong(OrderProduct::getNetPaidAmount)
			.sum();
		if(cancelAmountTotal <= 0) {
			throw new IllegalArgumentException("PG 취소 금액이 0이하일 수 없습니다.");
		}
		return cancelAmountTotal;
	}

	private static List<Long> extractCancelableIds(List<OrderProduct> cancelableOrderProducts, Long orderId) {
		List<Long> cancelableOrderProductIds = cancelableOrderProducts.stream()
			.map(OrderProduct::getId)
			.sorted()
			.toList();

		if (cancelableOrderProductIds.isEmpty()) {
			throw new IllegalArgumentException("취소 가능한 주문상품이 없습니다. orderId = " + orderId);
		}
		return cancelableOrderProductIds;
	}

	private void createCancelRequestWithFingerprint(PaymentFullCancelRequest request, List<Long> cancelableOrderProductIds, Long paymentId,
		long cancelAmountTotal) {
		String cancelFingerPrint = PaymentCancel.createFingerPrint(CancelType.FULL, request.getPaymentId(),
			cancelableOrderProductIds);
		paymentCancelRepo.saveAndFlush(PaymentCancel.create(paymentId, cancelAmountTotal, request.getIdemKey(), request.getReason(),
			cancelFingerPrint));
	}

	private void checkOrderStatus(Long orderId) {
		Order order = orderRepo.findById(orderId)
			.orElseThrow(() ->
				new OrderNotFoundException(ErrorCode.NOT_FOUND_ORDER, orderId)
			);
		if (!order.canCancelAnyProduct()) {
			throw new IllegalArgumentException("해당 주문은 취소할 수 없습니다. orderId = " + orderId);
		}
	}

	private Payment getPaymentAndCheckStatus(Long paymentId) {
		Payment payment = paymentRepo.findById(paymentId)
			.orElseThrow(() ->
				new PaymentNotFoundException(ErrorCode.NOT_FOUND_PAYMENT, paymentId));
		if (!payment.canStartCancel()) {
			throw new IllegalArgumentException("해당 결제는 취소할 수 없습니다. paymentId = " + paymentId);
		}
		return payment;
	}

	public PaymentCancelResponse cancelPayment(CancelCommand command, PaymentGatewayCancelResponse pgResponse) {
		return null;
	}
}
