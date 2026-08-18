package kr.hhplus.be.server.application.point;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.domain.common.ReservationReleaseReason;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.point.Point;
import kr.hhplus.be.server.domain.point.exception.PointNotFoundException;
import kr.hhplus.be.server.domain.pointReservation.PointReservation;
import kr.hhplus.be.server.domain.pointReservation.PointReserveStatus;
import kr.hhplus.be.server.domain.pointReservation.exception.PointReservationNotFoundException;
import kr.hhplus.be.server.exception.BusinessException;
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
		if (order.getPointUsedTotal() == 0)
			return;
		// 포인트 사용 확정(간단 버전)
		PointReservation pr = pointReservationRepo.findByOrderIdForUpdate(order.getId())
			.orElseThrow(() ->
				new PointReservationNotFoundException(ErrorCode.POINT_RESERVATION_NOT_FOUND, order.getId()));
		if (pr.getStatus() == PointReserveStatus.CONFIRMED)
			return;
		if (pr.getStatus() == PointReserveStatus.RELEASED) {
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


	// 1. reservation 없음 → 새로 예약
	// 2. 요청 = 0 → 기존 예약 해제
	// 3. CONFIRMED → 변경 불가
	// 4. RESERVED + 같은 금액 → 유지
	// 5. 나머지
	// → 기존 예약 해제
	// → 새 금액 예약
	// 나머지란
	// RESERVED + 금액 변경
	// RELEASED + 새로운 금액
	public long reserveOrReplace(Order order, long pointUseAmount) {
		validatePointUseAmount(pointUseAmount);

		Long orderId = order.getId();
		Long userId = order.getUser().getId();

		PointReservation reservation =
			pointReservationRepo
				.findByOrderIdForUpdate(orderId)
				.orElse(null);

		// 1. 기존 예약이 아예 없는 경우
		if (reservation == null) {

			if (pointUseAmount == 0L) {
				return 0L;
			}

			Point point = getPointForUpdate(userId);

			validateAvailablePoint(point, pointUseAmount);

			point.reservePoint(pointUseAmount);

			pointReservationRepo.save(
				PointReservation.reserve(
					orderId,
					userId,
					pointUseAmount
				)
			);

			return pointUseAmount;
		}

		if (reservation.isConfirmed()) {
			throw new IllegalStateException(
				"Confirmed point reservation cannot be replaced. "
					+ "orderId=" + orderId
					+ ", reservationId="
					+ reservation.getId()
			);
		}

		// 포인트를 안쓰기로 결정했는데 이전 예약이 존재하는 상황
		// 이전 예약을 해제 -> 포인트도 복구
		if (pointUseAmount == 0L) {
			if (reservation.release(
				ReservationReleaseReason.PAYMENT_REPREPARED
			)) {
				Point point = getPointForUpdate(userId);

				point.releaseReserve(
					reservation.getAmount()
				);
			}
			return 0L;
		}


		// 이전 예약이 존재 + 포인트 사용량의 변화가 없는 경우 -> 별도 예약 x
		if (reservation.isReserved() &&
			Objects.equals(
				reservation.getAmount(),
				pointUseAmount
			)
		) {
			return pointUseAmount;
		}



		Point point = getPointForUpdate(userId);
		// 기존 RESERVED라면 먼저 해제한다.
		// RELEASED라면 false이므로 아무것도 하지 않는다.
		if (reservation.release(
			ReservationReleaseReason.PAYMENT_REPREPARED
		)) {
			point.releaseReserve(
				reservation.getAmount()
			);
		}

		// 새로운 예약이 가능한지 검증
		validateAvailablePoint(
			point,
			pointUseAmount
		);

		point.reservePoint(
			pointUseAmount
		);

		reservation.reserveAgain(
			pointUseAmount
		);

		return pointUseAmount;
	}

	private void validatePointUseAmount(long pointUseAmount) {
		if (pointUseAmount < 0) {
			throw new IllegalArgumentException(
				"pointUseAmount cannot be negative. "
					+ "pointUseAmount="
					+ pointUseAmount
			);
		}
	}

	private Point getPointForUpdate(
		Long userId
	) {
		return pointRepo
			.findByUserIdForUpdate(userId)
			.orElseThrow(() ->
				new IllegalStateException(
					"Point does not exist. userId="
						+ userId
				)
			);
	}

	private void validateAvailablePoint(
		Point point,
		long pointUseAmount
	) {
		if (point.availablePoint()
			< pointUseAmount) {

			throw BusinessException.of(
				ErrorCode.POINT_NOT_ENOUGH,
				"Insufficient point. "
					+ "requestedPoint="
					+ pointUseAmount
					+ ", availablePoint="
					+ point.availablePoint()
			);
		}
	}


}
