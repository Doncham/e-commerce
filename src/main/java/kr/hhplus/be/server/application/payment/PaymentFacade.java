package kr.hhplus.be.server.application.payment;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import kr.hhplus.be.server.api.payment.request.PayRequest;
import kr.hhplus.be.server.api.payment.request.PayResponse;
import kr.hhplus.be.server.api.payment.request.PaymentGatewayRequest;
import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;
import kr.hhplus.be.server.application.order.OrderStatusService;
import kr.hhplus.be.server.application.payment.dto.PaymentAttempt;
import kr.hhplus.be.server.application.payment.pg.exception.PaymentGatewayTemporaryException;
import kr.hhplus.be.server.domain.order.OrderStatus;
import kr.hhplus.be.server.domain.order.exception.OrderAlreadyPaidOrderException;
import kr.hhplus.be.server.domain.payment.PaymentGatewayPort;
import kr.hhplus.be.server.domain.payment.exception.AlreadyPaidException;
import kr.hhplus.be.server.infrastructure.persistence.order.OrderRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentFacade implements PayUseCase{
	private final PaymentService command;
	private final PaymentGatewayPort pgPort;
	private final PaymentQueryService query;
	private final OrderStatusService orderStatusService;
	public PayResponse pay(PayRequest req) {
		Long orderId = req.getOrderId();
		// 엔티티를 밖으로 빼기 위한 DTO
		PaymentAttempt pending;
		try {
			pending = command.preparePayment(orderId);
		} catch (DataIntegrityViolationException
				 | AlreadyPaidException
				 | OrderAlreadyPaidOrderException e) {
			return query.findPayResult(orderId);
		}

		PaymentGatewayResponse pgResp = null;
		OrderStatus orderStatus = pending.getOrderStatus();
		if(orderStatus.equals(OrderStatus.PAYMENT_PENDING)) {
			try {
				// pg 호출
				pgResp = pgPort.requestPayment(
					PaymentGatewayRequest.forOrderPayment(
						pending.getOrderId(),
						pending.getAmount(),
						pending.getIdempotencyKey()
					)
				);
			} catch (PaymentGatewayTemporaryException e) {
				throw e;
			} catch (RuntimeException e) {
				// adapter가 예외 변환을 못 한 경우에도 일시 장애로 감싼다.
				throw new PaymentGatewayTemporaryException(
					"PG 호출 중 알 수 없는 오류가 발생했습니다. paymentId=" + pending.getPaymentId()
				);
			}
			// order 상태 변경 in Tx
			orderStatusService.pgComplete(pending.getOrderId());
		}

		// tx2: 결과 반영 + 확정/해제 + outbox
		return command.completePayment(pending.getPaymentId(), pgResp);
	}
}
