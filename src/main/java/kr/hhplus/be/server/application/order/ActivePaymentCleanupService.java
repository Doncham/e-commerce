package kr.hhplus.be.server.application.order;

import org.springframework.stereotype.Service;

import kr.hhplus.be.server.application.inventory.InventoryReservationService;
import kr.hhplus.be.server.application.point.PointReservationService;
import kr.hhplus.be.server.domain.inventoryReserve.InventoryReservationReleaseReason;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentStatus;
import kr.hhplus.be.server.exception.BusinessException;
import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.infrastructure.persistence.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ActivePaymentCleanupService {

	private final PaymentRepository paymentRepository;

	private final InventoryReservationService
		inventoryReservationService;

	// private final CouponReservationService
	// 	couponReservationService;

	private final PointReservationService
		pointReservationService;

	public void cleanupForOrderChange(Order order) {
		Payment payment = paymentRepository
			.findByOrderIdAndStatusInForUpdate(
				order.getId(),
				PaymentStatus.activeStatuses()
			)
			.orElseThrow(() ->
				new IllegalStateException(
					"PAYMENT_PENDING order has no active payment. "
						+ "orderId=" + order.getId()
				)
			);

		if (payment.isConfirming()) {
			throw BusinessException.of(
				ErrorCode.PAYMENT_CONFIRMING,
				"Payment confirmation is in progress. "
					+ "orderId=" + order.getId()
					+ ", paymentId="
					+ payment.getId()
			);
		}


		inventoryReservationService.releaseAll(
			order.getId(),
			InventoryReservationReleaseReason.ORDER_CHANGED
		);

		// couponReservationService.releaseAll(
		// 	payment.getId(),
		// 	ReservationReleaseReason.ORDER_CHANGED
		// );

		pointReservationService.releaseAll(
			payment.getId(),
			ReservationReleaseReason.ORDER_CHANGED
		);

		payment.replace();
		order.reopenDraft();
	}
}