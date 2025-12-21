package kr.hhplus.be.server.application.point;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;
import kr.hhplus.be.server.api.point.request.PointChargeRequest;
import kr.hhplus.be.server.api.point.response.PointChargeResponse;
import kr.hhplus.be.server.domain.payment.PaymentGatewayPort;
import kr.hhplus.be.server.domain.payment.PaymentGatewayStatus;
import kr.hhplus.be.server.domain.point.exception.PointChargeNegativeException;
import kr.hhplus.be.server.domain.pointcharge.ChargeStatus;
import kr.hhplus.be.server.domain.pointcharge.PointCharge;

@ExtendWith(MockitoExtension.class)
class PointFacadeTest {
	@InjectMocks
	private PointFacade pointFacade;
	@Mock
	private PointCommandService pointCommandService;
	@Mock
	private PaymentGatewayPort pgPort;
	@Mock
	private PointQueryService pointQueryService;

	@Test
	void givenValidPointRequest_whenCharge_thenReturnPointChargeResponse() {
		// given
		long amount = 1000L;
		String idempotencyKey = "key-123";
		long userId = 2L;
		long pointChargeId = 3L;
		String pgTransaction = "pg-123";
		PointChargeRequest request = PointChargeRequest.builder()
			.amount(amount)
			.idempotencyKey(idempotencyKey)
			.userId(userId)
			.build();
		long basePoint = 2000L;

		PointCharge pending = PointCharge.of(userId, amount, idempotencyKey);

		ReflectionTestUtils.setField(pending,"id",pointChargeId);
		PaymentGatewayResponse pgRes = PaymentGatewayResponse.of(pgTransaction,
			PaymentGatewayStatus.SUCCESS, amount);
		PointChargeResponse pointChargeResponse = PointChargeResponse.of(pending);

		when(pointCommandService.createPending(request)).thenReturn(pending);
		when(pgPort.requestPayment(any())).thenReturn(pgRes);
		when(pointCommandService.charge(pending.getId(), request, pgRes)).thenReturn(pointChargeResponse);

		// when
		PointChargeResponse res = pointFacade.charge(request);

		// then
		assertEquals(pointChargeResponse, res);
		verify(pgPort).requestPayment(any());
		verify(pointCommandService).charge(pending.getId(),request,pgRes);
		verify(pointCommandService).createPending(request);
	}

	@Test
	void givenAlreadyInsertedPointCharge_whenSecondChargeRequest_thenPointQueryServiceInvoke() {
		// given
		long amount = 1000L;
		String idempotencyKey = "key-123";
		long userId = 2L;
		long basePoint = 2000L;
		PointChargeRequest request = PointChargeRequest.builder()
			.amount(amount)
			.idempotencyKey(idempotencyKey)
			.userId(userId)
			.build();

		PointCharge pending = PointCharge.of(userId, amount, idempotencyKey);
		// 실패하는 경우는?? 두번째 요청이 락 풀려서 예외가 안터지겠지
		pending.success(amount + basePoint);
		PointChargeResponse pointChargeResponse = PointChargeResponse.of(pending);

		when(pointCommandService.createPending(request)).thenThrow(DataIntegrityViolationException.class);
		when(pointQueryService.findChargeResult(userId, idempotencyKey)).thenReturn(pointChargeResponse);
		// when
		PointChargeResponse res = pointFacade.charge(request);

		// then
		assertEquals(amount, res.getChargedAmount());
		assertEquals(ChargeStatus.SUCCEEDED, res.getStatus());
		assertEquals(basePoint + amount, res.getBalanceAfterChange());

		verify(pgPort, never()).requestPayment(any());
		verify(pointQueryService).findChargeResult(userId, idempotencyKey);
		verify(pointCommandService, never()).charge(anyLong(), any(), any());
	}

	@Test
	void givenNegativeChargeAmount_whenCharge_thenThrowException() {
		// given
		long amount = -20L;
		String idempotencyKey = "key-123";
		long userId = 2L;
		PointChargeRequest request = PointChargeRequest.builder()
			.amount(amount)
			.idempotencyKey(idempotencyKey)
			.userId(userId)
			.build();

		// when + then
		Assertions.assertThrows(PointChargeNegativeException.class,
			() -> pointFacade.charge(request));
	}

}