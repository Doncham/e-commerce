package kr.hhplus.be.server.application.payment;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.hhplus.be.server.api.payment.request.PayResponse;
import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;
import kr.hhplus.be.server.application.order.OrderPort;
import kr.hhplus.be.server.application.payment.dto.PaymentAttempt;
import kr.hhplus.be.server.domain.coupon.exception.CouponExpiredException;
import kr.hhplus.be.server.domain.coupon.exception.InsufficientCouponStockException;
import kr.hhplus.be.server.domain.coupon.exception.NotFoundCoupon;
import kr.hhplus.be.server.domain.coupon.exception.UserCouponLimitExceededException;
import kr.hhplus.be.server.domain.inventory.Inventory;
import kr.hhplus.be.server.domain.outbox.AggregateType;
import kr.hhplus.be.server.domain.outbox.EventType;
import kr.hhplus.be.server.infrastructure.persistence.inventory.InventoryRepository;
import kr.hhplus.be.server.domain.inventoryReserve.InventoryReservation;
import kr.hhplus.be.server.domain.inventoryReserve.InventoryReserveStatus;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.exception.OrderAlreadyPaidOrderException;
import kr.hhplus.be.server.domain.outbox.OutboxEvent;
import kr.hhplus.be.server.domain.outbox.PaymentCompletedPayload;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentGatewayStatus;
import kr.hhplus.be.server.domain.payment.PaymentPort;
import kr.hhplus.be.server.domain.point.Point;
import kr.hhplus.be.server.infrastructure.persistence.point.PointRepository;
import kr.hhplus.be.server.domain.point.exception.PointNotFoundException;
import kr.hhplus.be.server.domain.pointReservation.PointReservation;
import kr.hhplus.be.server.domain.pointReservation.PointReserveStatus;
import kr.hhplus.be.server.domain.pointReservation.exception.PointReservationNotFoundException;
import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.infrastructure.persistence.inventoryReserve.InventoryReserveRepository;
import kr.hhplus.be.server.infrastructure.persistence.outbox.OutboxEventRepository;
import kr.hhplus.be.server.infrastructure.persistence.pointReservation.PointReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentCommandService {
	private final PaymentPort paymentPort;
	private final OrderPort orderPort;
	private final OutboxEventRepository outboxEventRepository;
	private final ObjectMapper objectMapper;
	private final InventoryReserveRepository invReserveRepo;
	private final InventoryRepository invRepo;
	private final PointRepository pointRepo;
	private final PointReservationRepository pointReservationRepo;

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
		if(pgResp.getStatus() == PaymentGatewayStatus.SUCCESS) {
			if (!pgResp.getPaidAmount().equals(order.getPayAmount())) {
				payment.paymentFailed(pgResp.getPgTransactionId(), "PAY_AMOUNT_MISMATCH");
				order.failed();
				// 예약 해제
				releasePointReservations(order);
				releaseInventoryReservations(order);
				return PayResponse.of(order, payment);
			}

			// 결제 성공 기록
			payment.paymentSuccess(pgResp.getPgTransactionId(), LocalDateTime.now());

			// 예약 확정(재고/포인트사용/쿠폰)
			confirmPointReservations(order);
			confirmInventoryReservations(order);


			// 포인트 적립만 outbox
			publishPointEarnOutbox(order, pgResp.getPgTransactionId());

			order.paid();

			return PayResponse.of(order, payment);
		} else {
			// 결제 실패 기록 + 예약 해제
			payment.paymentFailed(pgResp.getPgTransactionId(), "PG_FAILED");

			releasePointReservations(order);
			releaseInventoryReservations(order);


			order.failed();
			return PayResponse.of(order, payment);
		}
	}
	private void confirmPointReservations(Order order) {
		if (order.getPointUsed() == 0) return;
		// 포인트 사용 확정(간단 버전)
		PointReservation pr = pointReservationRepo.findByOrderId(order.getId())
			.orElseThrow(() ->
				new PointReservationNotFoundException(ErrorCode.NOT_FOUND_POINT_RESERVATION, order.getId()));
		if(pr.getStatus() == PointReserveStatus.CONFIRMED) return;
		if(pr.getStatus() == PointReserveStatus.RELEASED) {
			throw new IllegalArgumentException("Reservation already released. orderId = " + order.getId());
		}
		Point point = pointRepo.findByUserIdForUpdate(order.getUser().getId())
			.orElseThrow(() -> new PointNotFoundException(ErrorCode.NOT_FOUND_POINT, order.getUser().getId()));

		pr.confirm();
		point.confirmUse(pr.getAmount());
	}

	private void confirmInventoryReservations(Order order) {
		List<InventoryReservation> reserves = invReserveRepo
			.findByOrderIdAndStatus(order.getId(), InventoryReserveStatus.RESERVED);
		if (reserves.isEmpty()) return; // 멱등/재시도 안전

		List<Long> invIds = reserves.stream().map(InventoryReservation::getInventoryId).sorted().toList();
		List<Inventory> inventories = invRepo.findByIdsForUpdate(invIds);
		Map<Long, Inventory> map = inventories.stream().collect(Collectors.toMap(Inventory::getId, it -> it));

		for (InventoryReservation r : reserves) {
			Inventory inv = map.get(r.getInventoryId());
			inv.confirmReserve(r.getQty());
			r.confirm();
		}

		// 쿠폰 확정도 같은 방식(RESERVED -> CONSUMED)
	}

	private void releasePointReservations(Order order) {
		if (order.getPointUsed() == 0) return;

		PointReservation pr = pointReservationRepo.findByOrderId(order.getId())
			.orElseThrow(() ->
				new PointReservationNotFoundException(ErrorCode.NOT_FOUND_POINT_RESERVATION, order.getId()));
		if(pr.getStatus() == PointReserveStatus.RELEASED) return;
		if(pr.getStatus() == PointReserveStatus.CONFIRMED) {
			throw new IllegalArgumentException("Reservation already confirmed. orderId = " + order.getId());
		}

		Point point = pointRepo.findByUserIdForUpdate(order.getUser().getId())
			.orElseThrow(() -> new PointNotFoundException(ErrorCode.NOT_FOUND_POINT, order.getUser().getId()));

		pr.release("PAYMENT_FAILED");
		point.releaseReserve(pr.getAmount());
	}
	private void releaseInventoryReservations(Order order) {
		List<InventoryReservation> reserves = invReserveRepo
			.findByOrderIdAndStatus(order.getId(), InventoryReserveStatus.RESERVED);
		if (reserves.isEmpty()) return; // 멱등/재시도 안전

		List<Long> invIds = reserves.stream().map(InventoryReservation::getInventoryId).sorted().toList();
		List<Inventory> inventories = invRepo.findByIdsForUpdate(invIds);
		Map<Long, Inventory> map = inventories.stream().collect(Collectors.toMap(Inventory::getId, it -> it));

		// 실무에서는 InventoryReservation의 상태가 RESERVED에서 변경될 때만 실제 재고 차감
		for(InventoryReservation r : reserves) {
			Inventory inv = map.get(r.getInventoryId());
			inv.releaseReserve(r.getQty());
			r.release("PAYMENT_FAILED");
		}
	}

	private void publishPointEarnOutbox(Order order, String pgTransactionId) {
		PaymentCompletedPayload payload = PaymentCompletedPayload.of(
			order.getUser().getId(),
			order.getId(),
			order.getPayAmount(),
			pgTransactionId
		);

		String payloadJson;
		try {
			payloadJson = objectMapper.writeValueAsString(payload);
		} catch (JsonProcessingException e) {
			throw new RuntimeException("Failed to serialize outbox payload", e);
		}
		OutboxEvent outboxEvent = OutboxEvent.of(
			AggregateType.ORDER,
			order.getId(),
			EventType.PAYMENT_COMPLETION_GIVE_POINT,
			payloadJson
		);
		outboxEventRepository.save(outboxEvent);
	}
}
