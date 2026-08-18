package kr.hhplus.be.server.application.payment;

import java.time.Clock;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.api.payment.request.PaymentPrepareRequest;
import kr.hhplus.be.server.api.payment.response.PaymentPrepareResponse;
import kr.hhplus.be.server.application.inventory.InventoryReservationService;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayType;
import kr.hhplus.be.server.application.point.PointReservationService;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.exception.BusinessException;
import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.infrastructure.persistence.order.OrderRepository;
import kr.hhplus.be.server.infrastructure.persistence.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentPrepareService {

	private static final long PAYMENT_EXPIRE_MINUTES = 15;

	private final OrderRepository orderRepo;
	private final PaymentRepository paymentRepo;

	private final InventoryReservationService inventoryReservationService;
	private final PointReservationService pointReservationService;

	private final Clock clock;

	@Transactional
	public PaymentPrepareResponse preparePayment(
		PaymentPrepareRequest request,
		String idempotencyKey
	) {
		Long userId = request.getUserId();
		// 1. Order를 먼저 잠근다.
		// 같은 Order에 대해 결제 준비 요청이 동시에 들어왔을 때 대비
		Order order = orderRepo
			.findByIdForUpdate(request.getOrderId())
			.orElseThrow(() ->
				BusinessException.of(
					ErrorCode.ORDER_NOT_FOUND,
					"Order not found. orderId="
						+ request.getOrderId()
				)
			);

		// 2.본인 주문 확인
		validateOrderOwner(
			order,
			userId
		);

		// 3.기존 주문 확인
		Payment payment = paymentRepo
			.findByOrderIdForUpdate(order.getId())
			.orElse(null);

		// 4.논리적으로 동일한 요청이라면(IdempotencyKey가 동일)
		// 예약을 다시 잡지 않고 기존 결과 반환
		if (isSamePrepareRetry(
			payment,
			idempotencyKey
		)) {
			return PaymentPrepareResponse.from(
				payment
			);
		}

		// 5. 새로운 결제 준비를 시작할 수 있는 상태인지 검사한다.
		validatePaymentCanPrepare(order, payment);

		/*
		 * 6. 재고 예약
		 *
		 * 이미 동일 Order에 RESERVED 상태의 재고가 존재한다면
		 * 다시 reserved를 증가시키면 안 된다.
		 *
		 * reserveOrKeep()이 이 판단을 책임지게 한다.
		 */
		inventoryReservationService
			.reserveOrKeep(order);

		/*
		 * 7. 기존 포인트 예약을 새로운 요청 기준으로 교체한다.
		 *
		 * 첫 결제 준비:
		 *   없음 → 새 예약
		 *
		 * 다시 결제 준비:
		 *   기존 예약 해제 → 새 금액 예약
		 */
		long reservedPointAmount = pointReservationService
				.reserveOrReplace(
					order,
					request.getPointUseAmount()
				);

		/*
		 * 8. 최종 PG 결제 금액 계산
		 *
		 * 지금은 쿠폰을 제외했으므로:
		 *
		 * 상품 총액 - 사용 포인트
		 */
		long paymentAmount =
			calculatePaymentAmount(
				order.getItemTotal(),
				reservedPointAmount
			);

		LocalDateTime expiresAt =
			LocalDateTime.now(clock)
				.plusMinutes(PAYMENT_EXPIRE_MINUTES);

		/*
		 * 9. Payment가 없으면 최초 생성,
		 * 있으면 동일 Payment를 다시 READY로 준비한다.
		 */
		if (payment == null) {
			payment = Payment.createReady(
				order,
				paymentAmount,
				PaymentGatewayType.TOSS,
				idempotencyKey,
				expiresAt
			);

			paymentRepo.save(payment);

		} else {
			payment.prepareAgain(
				paymentAmount,
				request.getGatewayType(),
				idempotencyKey,
				expiresAt
			);
		}

		/*
		 * 10. 재고/포인트 예약까지 성공했으므로
		 * Order를 결제 대기 상태로 변경한다.
		 */
		order.applyPaymentPreparation(0L, reservedPointAmount, paymentAmount);

		return PaymentPrepareResponse.from(
			payment
		);
	}

	private void validateOrderOwner(
		Order order,
		Long userId
	) {
		if (!order.getUser()
			.getId()
			.equals(userId)) {

			throw BusinessException.of(
				ErrorCode.ORDER_NOT_FOUND,
				"Order not found or not owned by user. "
					+ "orderId=" + order.getId()
					+ ", userId=" + userId
			);
		}
	}

	private boolean isSamePrepareRetry(
		Payment payment,
		String idempotencyKey
	) {
		if (payment == null) {
			return false;
		}

		// READY 상태의 동일 prepare 요청만 멱등 재시도로 본다.
		// INVALIDATED/FAILED/EXPIRED 상태가
		// 우연히 같은 key를 가진다고 멱등처리 해주면 안됨.
		return payment.isReady()
			&& payment.hasSameIdempotencyKey(
			idempotencyKey
		);
	}

	/*
	 * 재준비 가능한 상태
	 *
	 * READY       → 쿠폰/포인트 조건 변경 가능
	 * RESET       → Order 변경 후 다시 결제 가능
	 * FAILED      → 결제 실패 후 재결제 가능
	 * EXPIRED     → 만료 후 재결제 가능
	 */
	private void validatePaymentCanPrepare(
		Order order,
		Payment payment
	) {
		// 이미 결제가 완료된 주문은 다시 prepare할 수 없다.
		if (order.isPaid()) {
			throw BusinessException.of(
				ErrorCode.ORDER_ALREADY_PAID,
				"Order already paid. orderId="
					+ order.getId()
			);
		}

		if (payment == null) {
			return;
		}

		// CONFIRMING은 PG가 이미 결제 성공 처리했을 수 있기 때문에
		// Payment 내용을 덮어쓰거나, 새로운 결제를 준비하면 안 된다.
		if (payment.isConfirming()) {
			throw BusinessException.of(
				ErrorCode.PAYMENT_CONFIRMING,
				"Payment confirmation is in progress. "
					+ "paymentId="
					+ payment.getId()
			);
		}

		if (payment.isSuccess()
			|| payment.isPartialCanceled()
			|| payment.isCanceled()) {

			throw BusinessException.of(
				ErrorCode.PAYMENT_NOT_PREPARABLE,
				"Payment cannot be prepared again. "
					+ "paymentId="
					+ payment.getId()
					+ ", status="
					+ payment.getStatus()
			);
		}
	}

	private long calculatePaymentAmount(
		long itemTotal,
		long pointUsedAmount
	) {
		long paymentAmount =
			itemTotal - pointUsedAmount;

		if (paymentAmount < 0) {
			throw BusinessException.of(
				ErrorCode.POINT_AMOUNT_NOT_VALID,
				"Point use amount exceeds item total. "
					+ "itemTotal=" + itemTotal
					+ ", pointUsedAmount=" + pointUsedAmount
			);
		}

		return paymentAmount;
	}
}