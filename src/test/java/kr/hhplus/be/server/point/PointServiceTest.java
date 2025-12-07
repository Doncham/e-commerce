package kr.hhplus.be.server.point;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import kr.hhplus.be.server.api.payment.request.PaymentGatewayRequest;
import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;
import kr.hhplus.be.server.api.point.request.PointChargeRequest;
import kr.hhplus.be.server.api.point.response.PointChargeResponse;
import kr.hhplus.be.server.application.point.PointService;
import kr.hhplus.be.server.domain.payment.PaymentGatewayPort;
import kr.hhplus.be.server.domain.payment.PaymentGatewayStatus;
import kr.hhplus.be.server.domain.point.Point;
import kr.hhplus.be.server.domain.point.PointRepository;
import kr.hhplus.be.server.domain.point.exception.PointAmountNotValidException;
import kr.hhplus.be.server.domain.pointcharge.PointCharge;
import kr.hhplus.be.server.domain.pointhistory.ChangeType;
import kr.hhplus.be.server.domain.pointhistory.PointHistory;
import kr.hhplus.be.server.domain.pointhistory.PointHistoryRepository;
import kr.hhplus.be.server.domain.pointhistory.SourceType;
import kr.hhplus.be.server.infrastructure.persistence.pointcharge.PointChargeRepository;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {
	@InjectMocks
	private PointService pointService;
	@Mock
	private PointRepository pointRepository;
	@Mock
	private PointHistoryRepository pointHistoryRepository;
	@Mock
	private PointChargeRepository pointChargeRepository;
	@Mock
	private PaymentGatewayPort pgPort;

	// 포인트 지급 테스트
	@Test
	void givenValidUserAndPoint_whenChargePoint_thenIncreaseBalance() {
		// given
		long userId = 1L;
		long orderAmount = 10000L;
		long orderId = 2L;
		long balance = 2000L;
		Point point = Point.createPoint(userId);
		// 기존 포인트 초기화
		point.increaseBalance(balance);

		// 포인트 있는지 확인해서 없으면 만들어줌.
		when(pointRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(point));

		// when
		pointService.earnForOrder(userId, orderId, orderAmount);
		ArgumentCaptor<PointHistory> pointHistoryCaptor = ArgumentCaptor.forClass(PointHistory.class);

		// then
		verify(pointHistoryRepository).save(pointHistoryCaptor.capture());
		PointHistory savedPointHistory = pointHistoryCaptor.getValue();
		assertEquals(ChangeType.EARN, savedPointHistory.getChangeType());
		assertEquals(balance + orderAmount / 100, savedPointHistory.getBalanceAfterChange()); // 1% 적립
		assertEquals(SourceType.ORDER, savedPointHistory.getSourceType());
	}
	// 포인트 충전 실패 테스트 - 음수 충전
	@Test
	void givenNegativePointAmount_whenChargePoint_thenThrowException() {
		// given
		long userId = 1L;
		long orderAmount = -5000L; // 음수 금액
		long orderId = 2L;

		// when & then
		Assertions.assertThrows(PointAmountNotValidException.class, () -> {
			pointService.earnForOrder(userId, orderId, orderAmount);
		});
		verify(pgPort, never()).requestPayment(any(PaymentGatewayRequest.class));
	}

	// 포인트 충전 테스트(유저가 직접하는거 not order)
	@Test
	void givenValidaPoint_whenChargePoint_thenUserPointIncreased() {
		// given
		long userId = 1L;
		long chargeAmount = 5000L;
		String idempotencyKey = "unique-key-123";
		String pgTransactionId = "pg-tx-456";
		Long pointId = 15L;
		Point point = Point.createPoint(userId);
		point.increaseBalance(3000L); // 기존 포인트
		PointChargeRequest request = PointChargeRequest.builder()
			.amount(chargeAmount)
			.userId(userId)
			.idempotencyKey(idempotencyKey)
			.build();

		PointCharge pointCharge = createPointCharge(userId, chargeAmount, idempotencyKey, pointId);

		PaymentGatewayResponse pgResponse = PaymentGatewayResponse.of(pgTransactionId,
			PaymentGatewayStatus.SUCCESS, chargeAmount);

		when(pointRepository.findByUserIdForUpdate(userId))
			.thenReturn(Optional.of(point));
		when(pointChargeRepository.findByUserIdAndIdempotencyKey(userId,idempotencyKey))
			.thenReturn(Optional.empty());
		when(pointChargeRepository.save(any()))
			.thenReturn(pointCharge);
		when(pgPort.requestPayment(any()))
			.thenReturn(pgResponse);

		// when
		PointChargeResponse response = pointService.charge(request);

		// then
		ArgumentCaptor<PointHistory> captor = ArgumentCaptor.forClass(PointHistory.class);
		verify(pointHistoryRepository).save(captor.capture());
		PointHistory saved = captor.getValue();
		assertEquals(userId, saved.getUserId());
		assertEquals(ChangeType.EARN, saved.getChangeType());
		assertEquals(chargeAmount, saved.getAmount());
		assertEquals(8000L, saved.getBalanceAfterChange());
		assertEquals(SourceType.CHARGE, saved.getSourceType());

		assertEquals(userId, response.getUserId());
		assertEquals(chargeAmount, response.getChargedAmount());
		assertEquals(8000L, response.getBalanceAfterChange()); // 3000 + 5000
		verify(pointHistoryRepository).save(any(PointHistory.class));

		verify(pgPort).requestPayment(any(PaymentGatewayRequest.class));
		verify(pointChargeRepository).save(any(PointCharge.class));
	}
	// 음수 충전 시 예외 발생 테스트
	@Test
	void givenNegativeChargeAmount_whenChargePoint_thenThrowException() {
		// given
		long userId = 1L;
		long chargeAmount = -2000L; // 음수 금액
		PointChargeRequest request = PointChargeRequest.builder()
			.amount(chargeAmount)
			.userId(userId)
			.build();

		// when & then
		Assertions.assertThrows(PointAmountNotValidException.class, () -> {
			pointService.charge(request);
		});
	}

	// pointCharge가 이미 존재할 때 멱등성이 잘 지켜지는지 테스트
	@Test
	void givenConcurrentPointChargeInsert_whenChargePoint_thenThrowUniqueViolation() {
		// given
		long userId = 1L;
		long chargeAmount = 5000L;
		String idempotencyKey = "unique-key-123";
		Long pointChargeId = 10L;
		Point point = Point.createPoint(userId);
		point.increaseBalance(3000L); // 기존 포인트
		PointCharge existingPointCharge = createPointCharge(userId, chargeAmount, idempotencyKey, pointChargeId);

		PointChargeRequest request = PointChargeRequest.builder()
			.amount(chargeAmount)
			.userId(userId)
			.idempotencyKey(idempotencyKey)
			.build();

		when(pointRepository.findByUserIdForUpdate(userId))
			.thenReturn(Optional.of(point)).thenReturn((Optional.of(point)));
		when(pointChargeRepository.save(any()))
			.thenThrow(new DataIntegrityViolationException("Unique constraint violation")); // 이미 존재한다고 가정
		when(pointChargeRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey))
			.thenReturn(Optional.empty()).thenReturn((Optional.of(existingPointCharge)));

		// when
		PointChargeResponse response = pointService.charge(request);

		// then
		assertEquals(userId, response.getUserId());
		assertEquals(chargeAmount, response.getChargedAmount());
		assertEquals(8000L, response.getBalanceAfterChange()); // 3000 + 5000
		verify(pgPort, never()).requestPayment(any(PaymentGatewayRequest.class)); // PG 요청 안함
	}

	// pg가 실패했을 때 예외 발생 테스트 - 일단 RuntimeException, 이거는 일단 PG 설계 구체화 하라고 하면 작성하는걸로~
	@Test
	void givenFailedPaymentGateway_whenChargePoint_thenThrowException() {

	}


	private PointCharge createPointCharge(long userId, long amount, String idempotencyKey, Long id) {
		PointCharge pointCharge = PointCharge.of(userId, amount, idempotencyKey);
		ReflectionTestUtils.setField(pointCharge, "id", id);
		return pointCharge;
	}
}