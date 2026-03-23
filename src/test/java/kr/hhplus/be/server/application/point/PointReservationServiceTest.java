package kr.hhplus.be.server.application.point;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.point.Point;
import kr.hhplus.be.server.domain.point.exception.PointNotFoundException;
import kr.hhplus.be.server.domain.pointReservation.PointReservation;
import kr.hhplus.be.server.domain.pointReservation.PointReserveStatus;
import kr.hhplus.be.server.domain.pointReservation.exception.PointReservationNotFoundException;
import kr.hhplus.be.server.domain.user.User;
import kr.hhplus.be.server.infrastructure.persistence.point.PointRepository;
import kr.hhplus.be.server.infrastructure.persistence.pointReservation.PointReservationRepository;

@ExtendWith(MockitoExtension.class)
class PointReservationServiceTest {

	@InjectMocks
	private PointReservationService pointReservationService;
	// 클래스명을 PointReservationService로 바꾸면 여기 타입도 같이 바꾸면 됨

	@Mock
	private PointRepository pointRepo;

	@Mock
	private PointReservationRepository pointReservationRepo;

	@Mock
	private Order order;

	@Mock
	private User user;

	@Mock
	private PointReservation reservation;

	@Mock
	private Point point;

	@Test
	void confirm_whenPointUsedIsZero_thenDoNothing() {
		// given
		when(order.getPointUsedTotal()).thenReturn(0L);

		// when
		pointReservationService.confirm(order);

		// then
		verifyNoInteractions(pointReservationRepo, pointRepo);
	}

	@Test
	void confirm_whenReserved_thenConfirmReservationAndPointUse() {
		// given
		Long orderId = 1L;
		Long userId = 10L;
		Long reservedAmount = 3000L;

		when(order.getPointUsedTotal()).thenReturn(reservedAmount);
		when(order.getId()).thenReturn(orderId);
		when(order.getUser()).thenReturn(user);

		when(user.getId()).thenReturn(userId);
		when(pointReservationRepo.findByOrderId(orderId)).thenReturn(Optional.of(reservation));
		when(reservation.getStatus()).thenReturn(PointReserveStatus.RESERVED);
		when(reservation.getAmount()).thenReturn(reservedAmount);
		when(pointRepo.findByUserIdForUpdate(userId)).thenReturn(Optional.of(point));

		// when
		pointReservationService.confirm(order);

		// then
		verify(pointReservationRepo).findByOrderId(orderId);
		verify(pointRepo).findByUserIdForUpdate(userId);
		verify(reservation).confirm();
		verify(point).confirmUse(reservedAmount);
	}

	@Test
	void confirm_whenAlreadyConfirmed_thenReturnWithoutPointLock() {
		// given
		Long orderId = 1L;

		when(order.getPointUsedTotal()).thenReturn(1000L);
		when(order.getId()).thenReturn(orderId);
		when(pointReservationRepo.findByOrderId(orderId)).thenReturn(Optional.of(reservation));
		when(reservation.getStatus()).thenReturn(PointReserveStatus.CONFIRMED);

		// when
		pointReservationService.confirm(order);

		// then
		verify(pointReservationRepo).findByOrderId(orderId);
		verifyNoInteractions(pointRepo);
		verify(reservation, never()).confirm();
	}

	@Test
	void confirm_whenAlreadyReleased_thenThrowException() {
		// given
		Long orderId = 1L;

		when(order.getPointUsedTotal()).thenReturn(1000L);
		when(order.getId()).thenReturn(orderId);
		when(pointReservationRepo.findByOrderId(orderId)).thenReturn(Optional.of(reservation));
		when(reservation.getStatus()).thenReturn(PointReserveStatus.RELEASED);

		// when & then
		assertThrows(IllegalArgumentException.class, () -> pointReservationService.confirm(order));

		verify(pointReservationRepo).findByOrderId(orderId);
		verifyNoInteractions(pointRepo);
		verify(reservation, never()).confirm();
	}

	@Test
	void confirm_whenReservationNotFound_thenThrowException() {
		// given
		Long orderId = 1L;

		when(order.getPointUsedTotal()).thenReturn(1000L);
		when(order.getId()).thenReturn(orderId);
		when(pointReservationRepo.findByOrderId(orderId)).thenReturn(Optional.empty());

		// when & then
		assertThrows(PointReservationNotFoundException.class, () -> pointReservationService.confirm(order));

		verify(pointReservationRepo).findByOrderId(orderId);
		verifyNoInteractions(pointRepo);
	}

	@Test
	void confirm_whenPointNotFound_thenThrowException() {
		// given
		Long orderId = 1L;
		Long userId = 10L;

		when(order.getPointUsedTotal()).thenReturn(1000L);
		when(order.getId()).thenReturn(orderId);
		when(order.getUser()).thenReturn(user);

		when(user.getId()).thenReturn(userId);
		when(pointReservationRepo.findByOrderId(orderId)).thenReturn(Optional.of(reservation));
		when(reservation.getStatus()).thenReturn(PointReserveStatus.RESERVED);
		when(pointRepo.findByUserIdForUpdate(userId)).thenReturn(Optional.empty());

		// when & then
		assertThrows(PointNotFoundException.class, () -> pointReservationService.confirm(order));

		verify(pointReservationRepo).findByOrderId(orderId);
		verify(pointRepo).findByUserIdForUpdate(userId);
		verify(reservation, never()).confirm();
	}

	@Test
	void release_whenPointUsedIsZero_thenDoNothing() {
		// given
		when(order.getPointUsedTotal()).thenReturn(0L);

		// when
		pointReservationService.release(order, "PAYMENT_FAILED");

		// then
		verifyNoInteractions(pointReservationRepo, pointRepo);
	}

	@Test
	void release_whenReserved_thenReleaseReservationAndPointReserve() {
		// given
		Long orderId = 1L;
		Long userId = 10L;
		Long reservedAmount = 3000L;

		when(order.getPointUsedTotal()).thenReturn(reservedAmount);
		when(order.getId()).thenReturn(orderId);
		when(order.getUser()).thenReturn(user);

		when(user.getId()).thenReturn(userId);
		when(pointReservationRepo.findByOrderId(orderId)).thenReturn(Optional.of(reservation));
		when(reservation.getStatus()).thenReturn(PointReserveStatus.RESERVED);
		when(reservation.getAmount()).thenReturn(reservedAmount);
		when(pointRepo.findByUserIdForUpdate(userId)).thenReturn(Optional.of(point));

		// when
		pointReservationService.release(order, "PAYMENT_FAILED");

		// then
		verify(pointReservationRepo).findByOrderId(orderId);
		verify(pointRepo).findByUserIdForUpdate(userId);
		verify(reservation).release("PAYMENT_FAILED");
		verify(point).releaseReserve(reservedAmount);
	}

	@Test
	void release_whenAlreadyReleased_thenReturnWithoutPointLock() {
		// given
		Long orderId = 1L;

		when(order.getPointUsedTotal()).thenReturn(1000L);
		when(order.getId()).thenReturn(orderId);
		when(pointReservationRepo.findByOrderId(orderId)).thenReturn(Optional.of(reservation));
		when(reservation.getStatus()).thenReturn(PointReserveStatus.RELEASED);

		// when
		pointReservationService.release(order, "PAYMENT_FAILED");

		// then
		verify(pointReservationRepo).findByOrderId(orderId);
		verifyNoInteractions(pointRepo);
		verify(reservation, never()).release(anyString());
	}

	@Test
	void release_whenAlreadyConfirmed_thenThrowException() {
		// given
		Long orderId = 1L;

		when(order.getPointUsedTotal()).thenReturn(1000L);
		when(order.getId()).thenReturn(orderId);
		when(pointReservationRepo.findByOrderId(orderId)).thenReturn(Optional.of(reservation));
		when(reservation.getStatus()).thenReturn(PointReserveStatus.CONFIRMED);

		// when & then
		assertThrows(IllegalArgumentException.class, () -> pointReservationService.release(order, "PAYMENT_FAILED"));

		verify(pointReservationRepo).findByOrderId(orderId);
		verifyNoInteractions(pointRepo);
		verify(reservation, never()).release(anyString());
	}
}