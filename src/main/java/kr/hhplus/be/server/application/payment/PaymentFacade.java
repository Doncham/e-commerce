package kr.hhplus.be.server.application.payment;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import kr.hhplus.be.server.api.payment.request.PayRequest;
import kr.hhplus.be.server.api.payment.request.PayResponse;
import kr.hhplus.be.server.api.payment.request.PaymentGatewayRequest;
import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;
import kr.hhplus.be.server.application.payment.dto.PaymentAttempt;
import kr.hhplus.be.server.domain.order.exception.OrderAlreadyPaidOrderException;
import kr.hhplus.be.server.domain.payment.PaymentGatewayPort;
import kr.hhplus.be.server.domain.payment.exception.AlreadyPaidException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentFacade implements PayUseCase{
	private final PaymentCommandService command;
	private final PaymentGatewayPort pgPort;
	private final PaymentQueryService query;
	public PayResponse pay(PayRequest req) {
		Long orderId = req.getOrderId();
		String idemKey = req.getIdempotencyKey();
		// 엔티티를 밖으로 빼기 위한 DTO
		PaymentAttempt pending;
		try {
			pending = command.preparePayment(orderId, idemKey);
		} catch (DataIntegrityViolationException
				 | AlreadyPaidException
				 | OrderAlreadyPaidOrderException e) {
			return query.findPayResult(orderId, idemKey);
		}

		// pg 호출
		PaymentGatewayResponse pgResp = pgPort.requestPayment(
			PaymentGatewayRequest.forOrderPayment(pending.getOrderId(), pending.getAmount(), idemKey)
		);

		// tx2: 결과 반영 + 확정/해제 + outbox
		return command.completePayment(pending.getPaymentId(), pgResp);
	}
}
