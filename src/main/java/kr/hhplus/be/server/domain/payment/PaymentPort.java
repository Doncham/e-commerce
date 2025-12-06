package kr.hhplus.be.server.domain.payment;

import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentStatus;

public interface PaymentPort {
	Payment save(Payment payment);
	Payment loadLastSuccessPaymentByOrderId(Long orderId, PaymentStatus status);
}
