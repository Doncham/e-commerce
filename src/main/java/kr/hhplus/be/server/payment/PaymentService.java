package kr.hhplus.be.server.payment;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.order.Order;
import kr.hhplus.be.server.order.OrderRepository;
import kr.hhplus.be.server.payment.exception.PaidOrderHavePaymentException;
import kr.hhplus.be.server.payment.exception.PayAmountMisMatchException;
import kr.hhplus.be.server.payment.request.PayResponse;
import kr.hhplus.be.server.payment.request.PaymentGatewayRequest;
import kr.hhplus.be.server.payment.response.PaymentGatewayResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
	private final PaymentGatewayClient pgClient;
	private final PaymentRepository paymentRepository;
	private final OrderRepository orderRepository;

	@Transactional
	public PayResponse pay(Long orderId) {
		Order order = orderRepository.findByIdForUpdate(orderId)
			.orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
		// 멱등성 보장: 이미 결제된 주문이면 바로 반환 or 예외
		if(order.isPaid()) {
			// 도메인 규칙: 결제된 주문은 반드시 결제 기록이 있어야 한다 + 실패한 결제 기록 존재 가능.
			Payment payment = paymentRepository.findTopByOrderIdAndStatusOrderByIdDesc(order.getId(), PaymentStatus.SUCCESS)
				.orElseThrow(
					() -> new PaidOrderHavePaymentException(ErrorCode.PAID_ORDER_MUST_HAVE_PAYMENT, orderId.toString()));
			return PayResponse.of(order, payment.getPgTransactionId());
		}

		PaymentGatewayRequest pgReq = PaymentGatewayRequest.of(order);
		// 나중에는 재시도 로직 추가 + 예외 처리 시 실패한 Payment를 생성해야겠다.
		PaymentGatewayResponse resp = pgClient.requestPayment(pgReq);

		if(resp.getStatus() == PaymentGatewayStatus.SUCCESS) {
			if(!resp.getPaidAmount().equals(order.getPayAmount())) {
				throw new PayAmountMisMatchException(ErrorCode.PAY_AMOUNT_MISMATCH, order.toString());
			}
			// order 상태 변경
			order.paid();
		} else {
			// 결제 실패 시
			order.failed();
		}
		Payment payment = Payment.createPayment(order, resp.getPaidAmount());
		if(resp.getStatus() == PaymentGatewayStatus.SUCCESS) {
			payment.paymentSuccess(resp.getPgTransactionId(), LocalDateTime.now());
		} else {
			payment.paymentFailed(resp.getPgTransactionId());
		}

		paymentRepository.save(payment);

		// 이후 성공/실패 응답 생성
		if(resp.getStatus() == PaymentGatewayStatus.SUCCESS) {
			return PayResponse.of(order, resp.getPgTransactionId());
		} else {
			return PayResponse.of(order, null);
		}
	}
}
