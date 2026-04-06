package kr.hhplus.be.server.infrastructure.persistence.payment;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import kr.hhplus.be.server.domain.payment.Payment;
import kr.hhplus.be.server.domain.payment.PaymentStatus;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
	Optional<Payment> findTopByOrderIdAndStatusOrderByIdDesc(Long orderId, PaymentStatus status);
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Payment p where p.id = :paymentId")
	Optional<Payment> findByIdForUpdate(@Param("paymentId") Long paymentId);

	Optional<Payment> findByOrderIdAndIdempotencyKey(Long orderId, String idempotencyKey);

	Long countByOrderIdAndIdempotencyKey(Long orderId, String idempotencyKey);

	@Query("""
		select p
		from Payment p
		join fetch p.order o
		join fetch o.orderProducts op
		left join fetch op.paymentCancel pc
		where p.id = :paymentId
		order by op.id asc
""")
	Optional<Payment> findByIdForPaymentDetailResponse(Long paymentId);
}
