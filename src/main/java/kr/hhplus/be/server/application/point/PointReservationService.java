package kr.hhplus.be.server.application.point;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.domain.common.ReservationReleaseReason;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.point.Point;
import kr.hhplus.be.server.domain.point.exception.PointNotFoundException;
import kr.hhplus.be.server.domain.pointReservation.PointReservation;
import kr.hhplus.be.server.domain.pointReservation.PointReserveStatus;
import kr.hhplus.be.server.domain.pointReservation.exception.PointReservationNotFoundException;
import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.infrastructure.persistence.point.PointRepository;
import kr.hhplus.be.server.infrastructure.persistence.pointReservation.PointReservationRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class PointReservationService {
	private final PointRepository pointRepo;
	private final PointReservationRepository pointReservationRepo;

	public void confirm(Order order) {
		if (order.getPointUsedTotal() == 0) return;
		// 포인트 사용 확정(간단 버전)
		PointReservation pr = pointReservationRepo.findByOrderIdForUpdate(order.getId())
			.orElseThrow(() ->
				new PointReservationNotFoundException(ErrorCode.POINT_RESERVATION_NOT_FOUND, order.getId()));
		if(pr.getStatus() == PointReserveStatus.CONFIRMED) return;
		if(pr.getStatus() == PointReserveStatus.RELEASED) {
			throw new IllegalArgumentException("Reservation already released. orderId = " + order.getId());
		}
		Point point = pointRepo.findByUserIdForUpdate(order.getUser().getId())
			.orElseThrow(() -> new PointNotFoundException(ErrorCode.POINT_NOT_FOUND, order.getUser().getId()));

		pr.confirm();
		point.confirmUse(pr.getAmount());
	}

	public void releaseAll(Order order, ReservationReleaseReason reason) {
		// 데이터 불일치가 숨겨질 수 있다.
		// if (order.getPointUsedTotal() == 0) return;

		PointReservation reservation =
			pointReservationRepo
				.findByOrderIdForUpdate(order.getId())
				.orElse(null);

		/*
		 * 사용 포인트가 없어서 애초에 예약이 생성되지 않은 정상 상황
		 */
		if (reservation == null) {
			if (order.getPointUsedTotal() == 0L) {
				return;
			}

			/*
			 * Order에는 사용 포인트가 기록됐는데
			 * PointReservation이 없는 내부 정합성 오류
			 */
			throw new IllegalStateException(
				"Point reservation does not exist. "
					+ "orderId=" + order.getId()
					+ ", pointUsedTotal="
					+ order.getPointUsedTotal()
			);
		}

		validateReservationOwner(order, reservation);

		/*
		 * RESERVED  -> RELEASED : true
		 * RELEASED  -> RELEASED : false, 멱등 처리
		 * CONFIRMED -> RELEASED : IllegalStateException
		 */
		boolean released = reservation.release(reason);

		if (!released) {
			return;
		}

		Point point = pointRepo
			.findByUserIdForUpdate(
				reservation.getUserId()
			)
			.orElseThrow(() ->
				new IllegalStateException(
					"Point does not exist. "
						+ "userId="
						+ reservation.getUserId()
						+ ", reservationId="
						+ reservation.getId()
				)
			);

		point.releaseReserve(
			reservation.getAmount()
		);
	}

	// 포인트 예약이 없는데 order.PointUsedTotal가 있다면 이건 정합성이 깨진거다.
	private void validateMissingReservation(
		Order order
	) {
		if (order.getPointUsedTotal() > 0) {
			throw new IllegalStateException(
				"Point reservation is missing. "
					+ "orderId=" + order.getId()
					+ ", pointUsedTotal="
					+ order.getPointUsedTotal()
			);
		}
	}

	private void validateReservationOwner(
		Order order,
		PointReservation reservation
	) {
		Long orderUserId =
			order.getUser().getId();

		if (!reservation.belongsTo(orderUserId)) {
			throw new IllegalStateException(
				"Point reservation user mismatch. "
					+ "orderId=" + order.getId()
					+ ", orderUserId=" + orderUserId
					+ ", reservationUserId="
					+ reservation.getUserId()
			);
		}
	}
}
