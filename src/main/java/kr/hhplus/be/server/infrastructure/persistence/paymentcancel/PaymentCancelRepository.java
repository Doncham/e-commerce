package kr.hhplus.be.server.infrastructure.persistence.paymentcancel;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import kr.hhplus.be.server.domain.paymentcancel.PaymentCancel;
@Repository
public interface PaymentCancelRepository extends JpaRepository<PaymentCancel, Long> {
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select pc from PaymentCancel pc where pc.id = :paymentCancelId")
	Optional<PaymentCancel> findByIdForUpdate(@Param("paymentCancelId") Long paymentCancelId);

	Optional<PaymentCancel> findByCancelFingerPrint(String fingerPrint);
}
