package kr.hhplus.be.server.application.order;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.application.inventory.InventoryReservationService;
import kr.hhplus.be.server.application.point.PointReservationService;
import kr.hhplus.be.server.domain.common.ReservationReleaseReason;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.exception.BusinessException;
import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.infrastructure.persistence.payment.PaymentRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentPreparationCleanupService {

	private final PaymentRepository paymentRepository;

	private final InventoryReservationService
		inventoryReservationService;

	// private final CouponReservationService
	// 	couponReservationService;

	private final PointReservationService
		pointReservationService;

	// ✅ 굳이 @tx를 써줘야 하나? 상위에서 쓰는거 같이 쓸텐데
	public void cleanupForOrderChange(Order order) {
		Payment payment = paymentRepository
			// 활성 Payment 가져오던건데 이제는 걍 가져오면 됨. 1:1
			.findByOrderIdForUpdate(order.getId())
			.orElseThrow(() ->
				new IllegalStateException(
					"PAYMENT_PENDING order has no active payment. "
						+ "orderId=" + order.getId()
				)
			);
		// 이거는 스케줄러가 처리해야하는 영역? or 재시도는 아마 다 실패한거겠지
		if (payment.isConfirming()) {
			throw BusinessException.of(
				ErrorCode.PAYMENT_CONFIRMING,
				"Payment confirmation is in progress. "
					+ "orderId=" + order.getId()
					+ ", paymentId="
					+ payment.getId()
			);
		}

		// Payment 상태가 SUCCESS인 경우에도 또 진행하는 걸 방지
		if (!payment.isReady()) {
			throw new IllegalStateException(
				"PAYMENT_PENDING order has invalid payment status. "
					+ "orderId=" + order.getId()
					+ ", paymentId=" + payment.getId()
					+ ", paymentStatus="
					+ payment.getStatus()
			);
		}

		inventoryReservationService.releaseAll(
			order.getId(),
			ReservationReleaseReason.ORDER_CHANGED
		);

		// couponReservationService.releaseAll(
		// 	payment.getId(),
		// 	ReservationReleaseReason.ORDER_CHANGED
		// );

		pointReservationService.releaseAll(
			order,
			ReservationReleaseReason.ORDER_CHANGED
		);

		payment.invalidateForOrderChange();

		order.reopenDraft();
	}
}