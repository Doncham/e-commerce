package kr.hhplus.be.server.application.payment;

import java.time.LocalDateTime;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.api.payment.request.PayResponse;
import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;
import kr.hhplus.be.server.application.order.OrderPort;
import kr.hhplus.be.server.application.payment.dto.PaymentAttempt;
import kr.hhplus.be.server.domain.coupon.exception.CouponExpiredException;
import kr.hhplus.be.server.domain.coupon.exception.InsufficientCouponStockException;
import kr.hhplus.be.server.domain.coupon.exception.NotFoundCoupon;
import kr.hhplus.be.server.domain.coupon.exception.UserCouponLimitExceededException;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.exception.OrderAlreadyPaidOrderException;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentGatewayStatus;
import kr.hhplus.be.server.domain.payment.PaymentPort;
import kr.hhplus.be.server.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCommandService {
	private final PaymentPort paymentPort;
	private final OrderPort orderPort;
	private final PaymentReservationProcessor reservationProcessor;
	private final PaymentOutboxPublisher outboxPublisher;

	@Transactional
	public PaymentAttempt preparePayment(Long orderId, String idemKey) {
		Order order = orderPort.loadOrderForUpdate(orderId);

		if(order.isPaid()) {
			// 이미 성공 결제면 예외
			throw new OrderAlreadyPaidOrderException(ErrorCode.ALREADY_PAID_ORDER, orderId);
		}

		Payment pending = Payment.createPayment(order, idemKey, order.getPayAmount());
		pending = paymentPort.saveAndFlush(pending); // 유니크 충돌 빠르게 확정

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
		Payment payment = paymentPort.loadForUpdate(paymentId);
		// order 상태를 변경할거니까 락을 걸어서 조회하는건가?
		Order order = orderPort.loadOrderForUpdate(payment.getOrder().getId());

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

		return succeedPayment(pgResp, payment, order);
	}
	private PayResponse succeedPayment(PaymentGatewayResponse pgResp, Payment payment, Order order) {
		LocalDateTime paidAt = LocalDateTime.now();
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
}
