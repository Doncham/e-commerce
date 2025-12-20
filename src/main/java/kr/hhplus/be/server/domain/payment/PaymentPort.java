package kr.hhplus.be.server.domain.payment;

public interface PaymentPort {
	Payment save(Payment payment);
	Payment loadLastSuccessPaymentByOrderId(Long orderId, PaymentStatus status);
	Payment saveAndFlush(Payment payment);
	Payment loadForUpdate(Long paymentId);
	Long countByOrderIdAndIdempotencyKey(Long orderId, String idempotencyKey);
}
