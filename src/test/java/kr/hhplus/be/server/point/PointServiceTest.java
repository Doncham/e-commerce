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
import org.springframework.test.util.ReflectionTestUtils;

import kr.hhplus.be.server.api.payment.request.PaymentGatewayRequest;
import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;
import kr.hhplus.be.server.api.point.request.PointChargeRequest;
import kr.hhplus.be.server.api.point.response.PointChargeResponse;
import kr.hhplus.be.server.application.point.PointCommandService;
import kr.hhplus.be.server.domain.payment.PaymentGatewayPort;
import kr.hhplus.be.server.domain.payment.PaymentGatewayStatus;
import kr.hhplus.be.server.domain.point.Point;
import kr.hhplus.be.server.domain.point.PointRepository;
import kr.hhplus.be.server.domain.point.exception.PointAmountNotValidException;
import kr.hhplus.be.server.domain.pointcharge.ChargeStatus;
import kr.hhplus.be.server.domain.pointcharge.PointCharge;
import kr.hhplus.be.server.domain.pointhistory.ChangeType;
import kr.hhplus.be.server.domain.pointhistory.PointHistory;
import kr.hhplus.be.server.domain.pointhistory.PointHistoryRepository;
import kr.hhplus.be.server.domain.pointhistory.SourceType;
import kr.hhplus.be.server.infrastructure.persistence.pointcharge.PointChargeRepository;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {
	@InjectMocks
	private PointCommandService pointCommandService;
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
		pointCommandService.earnForOrder(userId, orderId, orderAmount);
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
	void givenNegativePayAmount_whenEarnForOrder_thenThrowException() {
		// given
		long userId = 1L;
		long orderAmount = -5000L; // 음수 금액
		long orderId = 2L;

		// when & then
		assertThrows(PointAmountNotValidException.class, () -> {
			pointCommandService.earnForOrder(userId, orderId, orderAmount);
		});
		verify(pgPort, never()).requestPayment(any(PaymentGatewayRequest.class));
	}

	// 포인트 충전 테스트(유저가 직접하는거 not order)
	@Test
	void givenValidaPointChargeRequestAndPgResp_whenChargePoint_thenUserPointIncreased() {
		// given
		long userId = 1L;
		long chargeAmount = 5000L;
		Long pointChargeId = 2L;
		String idempotencyKey = "unique-key-123";
		String pgTransactionId = "pg-tx-456";
		Point point = Point.createPoint(userId);
		point.increaseBalance(3000L); // 기존 포인트
		PointChargeRequest request = PointChargeRequest.builder()
			.amount(chargeAmount)
			.userId(userId)
			.idempotencyKey(idempotencyKey)
			.build();

		PointCharge pointCharge = PointCharge.of(userId, chargeAmount, idempotencyKey);

		PaymentGatewayResponse pgResponse = PaymentGatewayResponse.of(pgTransactionId,
			PaymentGatewayStatus.SUCCESS, chargeAmount);

		when(pointChargeRepository.findById(pointChargeId)).thenReturn(Optional.of(pointCharge));
		when(pointRepository.findByUserIdForUpdate(userId))
			.thenReturn(Optional.of(point));
		when(pointChargeRepository.findById(pointChargeId))
			.thenReturn(Optional.of(pointCharge));


		// when
		PointChargeResponse response = pointCommandService.charge(pointChargeId, request, pgResponse);

		// then
		ArgumentCaptor<PointHistory> captor = ArgumentCaptor.forClass(PointHistory.class);
		verify(pointHistoryRepository).save(captor.capture());
		PointHistory saved = captor.getValue();
		assertEquals(userId, saved.getUserId());
		assertEquals(ChangeType.CHARGE, saved.getChangeType());
		assertEquals(chargeAmount, saved.getAmount());
		assertEquals(8000L, saved.getBalanceAfterChange());
		assertEquals(SourceType.CHARGE, saved.getSourceType());

		assertEquals(userId, response.getUserId());
		assertEquals(chargeAmount, response.getChargedAmount());
		assertEquals(8000L, response.getBalanceAfterChange()); // 3000 + 5000
		verify(pointHistoryRepository).save(any(PointHistory.class));

	}

	// pointCharge가 이미 존재할 때 멱등성이 잘 지켜지는지 테스트
	@Test
	void givenAlreadySucceededPointChargeInsert_whenChargePoint_thenReturnSuccessResponse() {
		// given
		long userId = 1L;
		long chargeAmount = 5000L;
		String idempotencyKey = "unique-key-123";
		Long pointChargeId = 10L;
		String pgTransactionId = "pg-tx-213";
		Long basePoint = 3000L;
		Point point = Point.createPoint(userId);
		point.increaseBalance(basePoint); // 기존 포인트
		point.increaseBalance(chargeAmount); // 동시성으로 인해 이미 충전된 상태 가정
		PointCharge existingPointCharge = createPointCharge(userId, chargeAmount, idempotencyKey, pointChargeId);
		existingPointCharge.success(basePoint + chargeAmount);

		PointChargeRequest request = PointChargeRequest.builder()
			.amount(chargeAmount)
			.userId(userId)
			.idempotencyKey(idempotencyKey)
			.build();

		PaymentGatewayResponse pgResponse = PaymentGatewayResponse.of(pgTransactionId,
			PaymentGatewayStatus.SUCCESS, chargeAmount);

		when(pointChargeRepository.findById(pointChargeId)).thenReturn(Optional.of(existingPointCharge));

		// when
		PointChargeResponse response = pointCommandService.charge(pointChargeId, request, pgResponse);
		assertEquals(chargeAmount, response.getChargedAmount());
		assertEquals(chargeAmount + basePoint, response.getBalanceAfterChange());
		verify(pointRepository, never()).findByUserIdForUpdate(anyLong());
		verify(pointHistoryRepository, never()).save(any());
	}

	// pg가 실패했을 때 멱등 처리
	@Test
	void givenFailedPaymentGateway_whenChargePoint_thenPointChargeFailAndNoPointHistory() {
		// given
		long userId = 1L;
		long chargeAmount = 5000L;
		String idempotencyKey = "unique-key-123";
		Long pointChargeId = 10L;
		String pgTransactionId = "pg-tx-213";
		Long basePoint = 3000L;
		Point point = Point.createPoint(userId);
		point.increaseBalance(basePoint); // 기존 포인트
		point.increaseBalance(chargeAmount); // 동시성으로 인해 이미 충전된 상태 가정
		PointCharge existingPointCharge = createPointCharge(userId, chargeAmount, idempotencyKey, pointChargeId);

		PointChargeRequest request = PointChargeRequest.builder()
			.amount(chargeAmount)
			.userId(userId)
			.idempotencyKey(idempotencyKey)
			.build();

		PaymentGatewayResponse pgResponse = PaymentGatewayResponse.of(pgTransactionId,
			PaymentGatewayStatus.FAILURE, chargeAmount);

		when(pointChargeRepository.findById(pointChargeId)).thenReturn(Optional.of(existingPointCharge));

		// when
		PointChargeResponse response = pointCommandService.charge(pointChargeId, request, pgResponse);
		assertEquals(chargeAmount, response.getChargedAmount());
		assertEquals(ChargeStatus.FAILED, response.getStatus());
		assertEquals("pg 결제 실패", response.getFailReason());
		verify(pointHistoryRepository, never()).save(any());
		verify(pointRepository, never()).findByUserIdForUpdate(userId);
	}


	private PointCharge createPointCharge(long userId, long amount, String idempotencyKey, Long pointChargeId) {
		PointCharge pointCharge = PointCharge.of(userId, amount, idempotencyKey);
		ReflectionTestUtils.setField(pointCharge, "id", pointChargeId);
		return pointCharge;
	}
}