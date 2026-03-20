package kr.hhplus.be.server.application.point;

import org.springframework.stereotype.Service;

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
public class PointReservationService {
	private final PointRepository pointRepo;
	private final PointReservationRepository pointReservationRepo;

	public void confirm(Order order) {
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

	public void release(Order order, String reason) {
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

		pr.release(reason);
		point.releaseReserve(pr.getAmount());
	}
}
