package kr.hhplus.be.server.application.payment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.api.payment.request.PayResponse;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.domain.order.OrderRepository;
import kr.hhplus.be.server.domain.order.exception.OrderNotFoundException;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentRepository;
import kr.hhplus.be.server.domain.payment.PaymentStatus;
import kr.hhplus.be.server.domain.payment.exception.PaymentNotFoundException;
import kr.hhplus.be.server.exception.ErrorCode;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentQueryService {
	private final PaymentRepository paymentRepository;
	private final OrderRepository orderRepository;

	@Transactional(readOnly = true)
	public PayResponse findPayResult(Long orderId, String idemKey) {
		Order order = orderRepository.findById(orderId)
			.orElseThrow(() -> new OrderNotFoundException(ErrorCode.NOT_FOUND_ORDER, orderId));

		// idemKey로 결제 시도 레코드가 있다면 그게 반환
		Payment payment = paymentRepository.findByOrderIdAndIdempotencyKey(orderId, idemKey)
			.orElse(null);

		// 해피 케이스 빠르게 리턴
		if(payment != null) {
			return PayResponse.of(order, payment);
		}
		// idemKey 레코드는 없는데 주문이 PAID라면 "성공 결제"를 복구해서 응답
		// order가 결제되었다면 이에 해당하는 payment가 무조건 존재한다 가정.
		// 현재 클라이언트한테 받은 idemKey는 order 결제에 사용된 키와 다를 수 있기 때문에 성공한 가장 최근 결제 반환
		if(order.isPaid()) {
			Payment success = paymentRepository.findTopByOrderIdAndStatusOrderByIdDesc(orderId, PaymentStatus.SUCCESS)
				.orElseThrow(() -> new IllegalArgumentException("PAID order must have SUCCESS payment"));
			return PayResponse.of(order, success);
		}

		// 아직 결제 시도도 없는 경우
		return PayResponse.builder()
			.orderId(order.getId())
			.amount(order.getPayAmount())
			.status(PaymentStatus.REQUESTED)
			.message("결제 정보 조회 중(요청 생성 전/지연 가능)")
			.build();
	}
}
