package kr.hhplus.be.server.payment.port.out;

import kr.hhplus.be.server.payment.domain.Payment;
import kr.hhplus.be.server.payment.domain.PaymentStatus;

public interface PaymentPort {
	Payment save(Payment payment);
	Payment loadLastSuccessPaymentByOrderId(Long orderId, PaymentStatus status);
}
