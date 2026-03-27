package kr.hhplus.be.server.application.payment;

import java.util.Objects;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import kr.hhplus.be.server.application.payment.dto.CancelCommand;
import kr.hhplus.be.server.application.payment.dto.PaymentCancelResponse;
import kr.hhplus.be.server.application.payment.dto.PaymentFullCancelRequest;
import kr.hhplus.be.server.application.payment.dto.PaymentPartialCancelRequest;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayCancelRequest;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayCancelResponse;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayPort;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayRouter;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayType;
import kr.hhplus.be.server.application.payment.pg.exception.PaymentCancelFailException;
import kr.hhplus.be.server.domain.payment.PaymentGatewayStatus;
import kr.hhplus.be.server.exception.ErrorCode;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentCancelFacade {
	private final PaymentService paymentService;
	private final PaymentGatewayRouter paymentGatewayRouter;


	public PaymentCancelResponse cancelFull(PaymentFullCancelRequest request) {
		// 검증
		CancelCommand command = null;
		try{
			command = paymentService.prepareFullCancel(request);
		} catch (DataIntegrityViolationException e) {
			// paymentCancel을 조회해서 status별 응답을 넘겨준다.
		}

		// pg 호출
		PaymentGatewayPort route = paymentGatewayRouter.route(command.getGatewayType());
		PaymentGatewayCancelResponse pgResponse = route.cancel(PaymentGatewayCancelRequest.from(command));

		if(pgResponse.getStatus() != PaymentGatewayStatus.SUCCESS) {
			// PaymentGatewayTemporaryException, PaymentGatewayRejectedException로 세분화해라
			throw new PaymentCancelFailException(ErrorCode.PAYMENT_CANCEL_FAILED, command.getPaymentId());
		}

		// 후처리
		return paymentService.cancelPayment(command, pgResponse);
	}

	public void cancelPartial(PaymentPartialCancelRequest request) {

	}
}
