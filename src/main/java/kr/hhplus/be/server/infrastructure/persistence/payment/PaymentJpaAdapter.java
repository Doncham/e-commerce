package kr.hhplus.be.server.infrastructure.persistence.payment;

import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.domain.payment.exception.PaymentNotFoundException;
import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentStatus;
import kr.hhplus.be.server.domain.payment.exception.PaidOrderHavePaymentException;
import kr.hhplus.be.server.domain.payment.PaymentPort;
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

	@Override
	public Payment saveAndFlush(Payment payment) {
		return paymentRepository.saveAndFlush(payment);
	}

	@Override
	public Payment loadForUpdate(Long paymentId) {
		return paymentRepository.findByIdForUpdate(paymentId)
			.orElseThrow(() ->
				new PaymentNotFoundException(ErrorCode.NOT_FOUNT_PAYMENT, paymentId));
	}

	@Override
	public Long countByOrderIdAndIdempotencyKey(Long orderId, String idempotencyKey) {
		return paymentRepository.countByOrderIdAndIdempotencyKey(orderId, idempotencyKey);
	}
}
