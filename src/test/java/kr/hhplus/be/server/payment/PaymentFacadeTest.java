package kr.hhplus.be.server.payment;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import kr.hhplus.be.server.api.payment.request.PayRequest;
import kr.hhplus.be.server.api.payment.request.PayResponse;
import kr.hhplus.be.server.api.payment.request.PaymentGatewayRequest;
import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;
import kr.hhplus.be.server.application.payment.PaymentCommandService;
import kr.hhplus.be.server.application.payment.PaymentFacade;
import kr.hhplus.be.server.application.payment.PaymentQueryService;
import kr.hhplus.be.server.application.payment.dto.PaymentAttempt;
import kr.hhplus.be.server.domain.order.exception.OrderAlreadyPaidOrderException;
import kr.hhplus.be.server.domain.payment.PaymentGatewayPort;
import kr.hhplus.be.server.domain.payment.PaymentGatewayStatus;

@ExtendWith(MockitoExtension.class)
public class PaymentFacadeTest {
	@InjectMocks
	private PaymentFacade paymentFacade;
	@Mock
	private PaymentCommandService command;
	@Mock
	private PaymentGatewayPort pgPort;
	@Mock
	private PaymentQueryService query;

	@Test
	void givenValidRequest_whenPay_thenPreparePgComplete_inOrder_andVerifyPgArgs() {
		// given
		Long orderId = 1L;
		String idemKey = "idem-123";
		Long paymentId = 10L;
		Long amount = 2500L;

		PayRequest req = PayRequest.of(idemKey, orderId);

		PaymentAttempt attempt = PaymentAttempt.of(orderId, paymentId, amount, idemKey);
		PaymentGatewayResponse pgResp = PaymentGatewayResponse.of("tx-1", PaymentGatewayStatus.SUCCESS, amount);
		PayResponse expected = PayResponse.builder()
			.orderId(orderId)
			.amount(amount)
			.transactionId("tx-1")
			.build();

		when(command.preparePayment(orderId, idemKey)).thenReturn(attempt);
		when(pgPort.requestPayment(any())).thenReturn(pgResp);
		when(command.completePayment(paymentId, pgResp)).thenReturn(expected);

		// when
		PayResponse res = paymentFacade.pay(req);

		// then: 결과
		Assertions.assertEquals(expected, res);

		// then: 호출 순서 + 인자 검증
		var inOrder = inOrder(command, pgPort);
		inOrder.verify(command).preparePayment(orderId, idemKey);

		ArgumentCaptor<PaymentGatewayRequest> captor = ArgumentCaptor.forClass(PaymentGatewayRequest.class);
		inOrder.verify(pgPort).requestPayment(captor.capture());
		PaymentGatewayRequest sent = captor.getValue();
		Assertions.assertEquals(amount, sent.getAmount());
		Assertions.assertEquals(idemKey, sent.getIdempotencyKey());

		inOrder.verify(command).completePayment(paymentId, pgResp);

		verify(query, never()).findPayResult(any(), any());
	}

	@Test
	void givenUniqueConflict_whenPreparePaymentThrows_thenFallbackToQuery_andNoPgCall() {
		// given
		Long orderId = 1L;
		String idemKey = "idem-123";
		PayRequest req = PayRequest.of(idemKey, orderId);

		when(command.preparePayment(orderId, idemKey)).thenThrow(new DataIntegrityViolationException("dup"));
		PayResponse fallback = PayResponse.builder().orderId(orderId).message("fallback").build();
		when(query.findPayResult(orderId, idemKey)).thenReturn(fallback);

		// when
		PayResponse res = paymentFacade.pay(req);

		// then
		assertSame(fallback, res);
		verify(pgPort, never()).requestPayment(any());
		verify(command, never()).completePayment(any(), any());
		verify(query).findPayResult(orderId, idemKey);
	}

	@Test
	void givenAlreadyPaidOrder_whenPreparePaymentThrows_thenFallbackToQuery() {
		// given
		Long orderId = 1L;
		String idemKey = "idem-123";
		PayRequest req = PayRequest.of(idemKey, orderId);

		when(command.preparePayment(orderId, idemKey))
			.thenThrow(mock(OrderAlreadyPaidOrderException.class));

		PayResponse fallback = PayResponse.builder().orderId(orderId).message("already paid").build();
		when(query.findPayResult(orderId, idemKey)).thenReturn(fallback);

		// when
		PayResponse res = paymentFacade.pay(req);

		// then
		assertSame(fallback, res);
		verify(pgPort, never()).requestPayment(any());
		verify(command, never()).completePayment(any(), any());
	}
}

