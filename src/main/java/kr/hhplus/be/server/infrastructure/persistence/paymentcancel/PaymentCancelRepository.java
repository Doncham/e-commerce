package kr.hhplus.be.server.infrastructure.persistence.paymentcancel;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.domain.paymentcancel.PaymentCancel;
@Repository
public interface PaymentCancelRepository extends JpaRepository<PaymentCancel, Long> {
}
