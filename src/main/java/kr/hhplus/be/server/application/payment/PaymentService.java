package kr.hhplus.be.server.application.payment;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.api.payment.request.PayResponse;
import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;
import kr.hhplus.be.server.application.inventory.InventoryService;
import kr.hhplus.be.server.application.payment.dto.PaymentAttempt;
import kr.hhplus.be.server.application.payment.dto.PaymentCancelRequest;
import kr.hhplus.be.server.application.payment.dto.PaymentCancelResponse;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayType;
import kr.hhplus.be.server.application.point.PointService;
import kr.hhplus.be.server.domain.coupon.exception.CouponExpiredException;
import kr.hhplus.be.server.domain.coupon.exception.InsufficientCouponStockException;
import kr.hhplus.be.server.domain.coupon.exception.NotFoundCoupon;
import kr.hhplus.be.server.domain.coupon.exception.UserCouponLimitExceededException;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.exception.OrderAlreadyPaidOrderException;
import kr.hhplus.be.server.domain.order.exception.OrderNotFoundException;
import kr.hhplus.be.server.domain.orderproduct.OrderProduct;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentGatewayStatus;
import kr.hhplus.be.server.domain.payment.PaymentStatus;
import kr.hhplus.be.server.domain.payment.dto.PaymentCancelJob;
import kr.hhplus.be.server.domain.payment.dto.PaymentDetailResponse;
import kr.hhplus.be.server.domain.payment.exception.PaymentNotFoundException;
import kr.hhplus.be.server.domain.paymentcancel.PaymentCancel;
import kr.hhplus.be.server.domain.paymentcancel.PaymentCancelStatus;
import kr.hhplus.be.server.domain.paymentcancel.exception.PaymentCancelPermanentException;
import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.infrastructure.persistence.order.OrderRepository;
import kr.hhplus.be.server.infrastructure.persistence.orderproduct.OrderProductRepository;
import kr.hhplus.be.server.infrastructure.persistence.payment.PaymentRepository;
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
	private final PointService pointService;
	private final InventoryService inventoryService;
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
	public PayResponse completePayment(Long paymentId, PaymentGatewayResponse pgResp) {
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
		payment.paymentFailed(reason);
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

	public String getFingerPrint(PaymentCancelRequest request) {
		Long paymentId = request.getPaymentId();
		List<Long> targetIds = verifyAndGetUniqueOrderProductIds(request.getOrderProductIds());
		return PaymentCancel.createFingerprint(paymentId, targetIds);
	}

	@Transactional
	public PaymentCancelJob prepareOrGetCancelJob(PaymentCancelRequest request) {
		Long paymentId = request.getPaymentId();
		Payment payment = getPaymentAndCheckCancelableForPrepare(paymentId);

		Long orderId = payment.getOrder().getId();
		getOrderAndCheckCancelableForPrepare(orderId);

		List<Long> targetIds = verifyAndGetUniqueOrderProductIds(request.getOrderProductIds());

		List<OrderProduct> orderProducts = orderProductRepo.findByIds(targetIds, orderId);
		if (orderProducts.size() != targetIds.size()) {
			throw new IllegalArgumentException("존재하지 않거나 해당 주문에 속하지 않는 주문상품이 포함되어 있습니다.");
		}

		validateAllCancelable(orderProducts);

		long cancelAmount = getCancelAmountTotalAndVerify(orderProducts);

		String fingerprint = PaymentCancel.createFingerprint(paymentId, targetIds);
		String snapshot = PaymentCancel.makeSnapshot(targetIds);
			PaymentCancel created = paymentCancelRepo.saveAndFlush(
				PaymentCancel.create(
					paymentId,
					cancelAmount,
					request.getIdemKey(),
					request.getReason(),
					fingerprint,
					snapshot
				)
			);

			return PaymentCancelJob.of(
				created,
				payment.getGatewayType(),
				payment.getPgTransactionId(),
				String.valueOf(orderId)
			);
	}
	@Transactional
	public PaymentCancelJob getPaymentCancel(PaymentCancelRequest request, String fingerprint) {
		Long paymentId = request.getPaymentId();
		Payment payment = getPaymentAndCheckCancelableForPrepare(paymentId);
		Long orderId = payment.getOrder().getId();

		PaymentCancel existing = paymentCancelRepo.findByCancelFingerPrint(fingerprint)
			.orElseThrow(() -> new IllegalStateException("유니크 충돌 후 기존 PaymentCancel을 찾지 못했습니다."));

		return PaymentCancelJob.of(
			existing,
			payment.getGatewayType(),
			payment.getPgTransactionId(),
			String.valueOf(orderId)
		);
	}

	private static void validateAllCancelable(List<OrderProduct> orderProducts) {
		boolean allCancelable = orderProducts.stream()
			.allMatch(OrderProduct::isCancelable);

		if (!allCancelable) {
			throw new IllegalArgumentException("이미 취소되었거나 취소 불가능한 주문상품이 포함되어 있습니다.");
		}
	}

	private static List<Long> verifyAndGetUniqueOrderProductIds(List<Long> requestedIds) {
		if (requestedIds == null || requestedIds.isEmpty()) {
			throw new IllegalArgumentException("취소할 주문상품이 없습니다.");
		}

		List<Long> uniqueOrderProductIds = requestedIds.stream()
			.distinct()
			.sorted()
			.toList();

		if (uniqueOrderProductIds.size() != requestedIds.size()) {
			throw new IllegalArgumentException("중복된 orderProductId가 포함되어 있습니다.");
		}
		return uniqueOrderProductIds;
	}

	@Transactional
	public PaymentCancelResponse completeCancelPayment(Long paymentCancelId) {
		PaymentCancel paymentCancel = getPaymentCancelAndCheckCancelable(paymentCancelId);

		Payment payment = getPaymentAndCheckCancelableForComplete(paymentCancel.getPaymentId());

		Long orderId = payment.getOrder().getId();
		Order order = getOrderAndCheckCancelableForComplete(orderId);

		List<OrderProduct> allOrderProducts = orderProductRepo.findByOrderIdForUpdate(orderId);
		List<OrderProduct> targetProducts = getOrderProductsAndCheckCancelable(paymentCancel,
			allOrderProducts);

		verifyCancelAmount(targetProducts, paymentCancel);

		targetProducts.forEach(op -> op.cancel(paymentCancel));

		boolean isFullCancel = allOrderProducts.stream()
			.noneMatch(OrderProduct::isCancelable);

		payment.applyCancel(paymentCancel.getCancelAmount());
		order.applyCancelResult(isFullCancel);

		pointService.restorePoint(order.getUser().getId(), targetProducts, orderId);
		inventoryService.restoreInventory(targetProducts);

		paymentCancel.success();

		return PaymentCancelResponse.from(payment, paymentCancel, order, targetProducts, isFullCancel);
	}

	private static void verifyCancelAmount(List<OrderProduct> targetProducts, PaymentCancel paymentCancel) {
		long recalculatedAmount = targetProducts.stream()
			.mapToLong(OrderProduct::getCancelableAmount)
			.sum();

		if (recalculatedAmount != paymentCancel.getCancelAmount()) {
			throw new PaymentCancelPermanentException("취소 금액이 일치하지 않습니다.");
		}
	}

	private static List<OrderProduct> getOrderProductsAndCheckCancelable(PaymentCancel paymentCancel,
		List<OrderProduct> allOrderProducts) {
		List<Long> targetIds = paymentCancel.getTargetOrderProductIds();

		HashSet<Long> targetIdSet = new HashSet<>(targetIds);
		List<OrderProduct> targetProducts = allOrderProducts.stream()
			.filter(op -> targetIdSet.contains(op.getId()))
			.toList();

		boolean allCancelable = targetProducts.stream().allMatch(OrderProduct::isCancelable);
		if (!allCancelable) {
			throw new PaymentCancelPermanentException("취소 불가능한 주문상품이 포함되어 있습니다.");
		}

		if (targetProducts.size() != targetIdSet.size()) {
			throw new PaymentCancelPermanentException("일부 주문상품을 찾을 수 없습니다.");
		}
		return targetProducts;
	}


	private Order getOrderAndCheckCancelableForComplete(Long orderId) {
		Order order = orderRepo.findByIdForUpdate(orderId)
			.orElseThrow(() -> new PaymentCancelPermanentException("존재하지 않는 주문입니다. orderId=" + orderId));

		if (!order.canCancelAnyProduct()) {
			throw new PaymentCancelPermanentException("해당 주문은 취소할 수 없습니다. orderId=" + orderId);
		}
		return order;
	}

	private Payment getPaymentAndCheckCancelableForComplete(Long paymentId) {
		Payment payment = paymentRepo.findByIdForUpdate(paymentId)
			.orElseThrow(() -> new PaymentCancelPermanentException("존재하지 않는 payment 입니다. paymentId=" + paymentId));

		if (!payment.canStartCancel()) {
			throw new PaymentCancelPermanentException("해당 결제는 취소할 수 없습니다. paymentId=" + paymentId);
		}
		return payment;
	}

	private PaymentCancel getPaymentCancelAndCheckCancelable(Long paymentCancelId) {
		PaymentCancel paymentCancel = paymentCancelRepo.findByIdForUpdate(paymentCancelId)
			.orElseThrow(() -> new PaymentCancelPermanentException("존재하지 않는 paymentCancel 입니다. paymentCancelId=" + paymentCancelId));
		if (!paymentCancel.isPgCancelCompleted()) {
			throw new PaymentCancelPermanentException("외부 취소가 완료되지 않은 PaymentCancel 입니다.");
		}
		if (paymentCancel.getStatus() != PaymentCancelStatus.PROCESSING) {
			throw new PaymentCancelPermanentException(
				"완료 처리할 수 없는 PaymentCancel 상태입니다. status=" + paymentCancel.getStatus());
		}
		return paymentCancel;
	}

	private static long getCancelAmountTotalAndVerify(List<OrderProduct> cancelableOrderProducts) {
		long cancelAmountTotal = cancelableOrderProducts.stream()
			.mapToLong(OrderProduct::getCancelableAmount)
			.sum();
		if(cancelAmountTotal <= 0) {
			throw new IllegalArgumentException("PG 취소 금액이 0이하일 수 없습니다.");
		}
		return cancelAmountTotal;
	}

	private void getOrderAndCheckCancelableForPrepare(Long orderId) {
		Order order = orderRepo.findById(orderId)
			.orElseThrow(() ->
				new OrderNotFoundException(ErrorCode.NOT_FOUND_ORDER, orderId)
			);
		if (!order.canCancelAnyProduct()) {
			throw new IllegalArgumentException("해당 주문은 취소할 수 없습니다. orderId = " + orderId);
		}
	}

	private Payment getPaymentAndCheckCancelableForPrepare(Long paymentId) {
		Payment payment = paymentRepo.findById(paymentId)
			.orElseThrow(() ->
				new PaymentNotFoundException(ErrorCode.NOT_FOUND_PAYMENT, paymentId));
		if (!payment.canStartCancel()) {
			throw new IllegalArgumentException("해당 결제는 취소할 수 없습니다. paymentId = " + paymentId);
		}
		return payment;
	}


	@Transactional(readOnly = true)
	public PaymentCancelResponse buildSucceededCancelResponse(Long paymentCancelId) {
		PaymentCancel paymentCancel = paymentCancelRepo.findById(paymentCancelId)
			.orElseThrow(() ->
				new PaymentCancelPermanentException("존재하지 않는 paymentCancel 입니다. paymentCancelId=" + paymentCancelId));

		if (paymentCancel.getStatus() != PaymentCancelStatus.SUCCEEDED) {
			throw new PaymentCancelPermanentException(
				"성공 응답을 복원할 수 없는 PaymentCancel 상태입니다. status=" + paymentCancel.getStatus()
			);
		}

		Payment payment = paymentRepo.findById(paymentCancel.getPaymentId())
			.orElseThrow(() ->
				new PaymentNotFoundException(ErrorCode.NOT_FOUND_PAYMENT, paymentCancel.getPaymentId()));

		Long orderId = payment.getOrder().getId();
		Order order = orderRepo.findById(orderId)
			.orElseThrow(() ->
				new OrderNotFoundException(ErrorCode.NOT_FOUND_ORDER, orderId));

		List<OrderProduct> targetProducts = orderProductRepo.findByPaymentCancelId(paymentCancel.getId());

		boolean fullCancel = payment.getStatus() == PaymentStatus.CANCELLED;

		return PaymentCancelResponse.from(payment, paymentCancel, order, targetProducts, fullCancel);
	}

}
