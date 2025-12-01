package kr.hhplus.be.server.payment.adapter;

import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.payment.domain.Payment;
import kr.hhplus.be.server.payment.domain.PaymentStatus;
import kr.hhplus.be.server.payment.exception.PaidOrderHavePaymentException;
import kr.hhplus.be.server.payment.port.out.PaymentPort;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PaymentJpaAdapter implements PaymentPort {
	private final PaymentRepository paymentRepository;

	@Override
	public Payment save(Payment payment) {
		return paymentRepository.save(payment);
	}

	@Override
	public Payment loadLastSuccessPaymentByOrderId(Long orderId, PaymentStatus status) {
		return paymentRepository
			.findTopByOrderIdAndStatusOrderByIdDesc(orderId, status)
			.orElseThrow(() -> new PaidOrderHavePaymentException(ErrorCode.PAID_ORDER_MUST_HAVE_PAYMENT, orderId.toString()));
	}
}
