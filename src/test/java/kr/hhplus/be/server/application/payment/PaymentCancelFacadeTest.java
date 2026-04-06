package kr.hhplus.be.server.application.payment;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.hhplus.be.server.application.payment.dto.PaymentCancelRequest;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayRouter;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayType;
import kr.hhplus.be.server.application.payment.pg.exception.PaymentGatewayTemporaryException;
import kr.hhplus.be.server.application.paymentCancel.PaymentCancelStatusService;
import kr.hhplus.be.server.domain.payment.PaymentGatewayPort;
import kr.hhplus.be.server.domain.payment.dto.PaymentCancelJob;
import kr.hhplus.be.server.domain.paymentcancel.exception.PaymentCancelTemporaryException;

@ExtendWith(MockitoExtension.class)
class PaymentCancelFacadeTest {

	@Mock
	private PaymentService paymentService;

	@Mock
	private PaymentGatewayRouter paymentGatewayRouter;

	@Mock
	private PaymentCancelStatusService paymentCancelStatusService;

	@Mock
	private PaymentGatewayPort paymentGatewayPort;

	@InjectMocks
	private PaymentCancelFacade paymentCancelFacade;

	@Test
	@DisplayName("PG 임시 실패면 BEFORE_PG retryable 로 기록하고 PaymentCancelTemporaryException 을 던진다")
	void cancelPayment_whenPgTemporaryFailure_marksRetryableBeforePg() {
		PaymentCancelRequest request = mock(PaymentCancelRequest.class);
		PaymentCancelJob job = mock(PaymentCancelJob.class);

		when(paymentService.prepareOrGetCancelJob(request)).thenReturn(job);

		when(job.isSucceeded()).thenReturn(false);
		when(job.isProcessing()).thenReturn(false);
		when(job.isPermanentFailed()).thenReturn(false);
		when(job.isPgCancelCompleted()).thenReturn(false);

		when(job.getPaymentCancelId()).thenReturn(1L);
		when(job.getGatewayType()).thenReturn(PaymentGatewayType.TOSS);
		when(job.getOriginalPgTransactionId()).thenReturn("pg-origin-tx");
		when(job.getMerchantOrderId()).thenReturn("100");
		when(job.getCancelIdempotencyKey()).thenReturn("cancel:1");
		when(job.getCancelAmount()).thenReturn(10000L);
		when(job.getReason()).thenReturn("단순 변심");

		when(paymentGatewayRouter.route(PaymentGatewayType.TOSS)).thenReturn(paymentGatewayPort);
		when(paymentGatewayPort.cancel(any()))
			.thenThrow(new PaymentGatewayTemporaryException("pg timeout"));

		assertThatThrownBy(() -> paymentCancelFacade.cancelPayment(request))
			.isInstanceOf(PaymentCancelTemporaryException.class)
			.hasMessageContaining("pg timeout");

		verify(paymentCancelStatusService).markProcessing(1L);
		verify(paymentCancelStatusService).failRetryableBeforePg(1L, "pg timeout");
		verify(paymentService, never()).completeCancelPayment(anyLong());
	}
}