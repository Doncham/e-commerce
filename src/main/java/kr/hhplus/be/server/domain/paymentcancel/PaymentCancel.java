package kr.hhplus.be.server.domain.paymentcancel;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
// {"payment_id", "idempotency_key"} 인덱스를 굳이 만들어야하나?
public class PaymentCancel {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;
	@Column(nullable = false)
	private Long paymentId;
	@Column(nullable = false)
	private String idempotencyKey;
	@Column(nullable = false)
	private Long cancelAmount;
	@Column(nullable = false)
	private String reason;
	@Column(unique = true)
	private String pgCancelTransactionId;

	private LocalDateTime canceledAt;

	@Enumerated(EnumType.STRING)
	private PaymentCancelStatus status;

	// 취소 실패 사유가 있네
	private String failReason;

	protected PaymentCancel(Long payment, Long cancelAmount, String idempotencyKey, String reason) {
		this.paymentId = payment;
		this.cancelAmount = cancelAmount;
		this.idempotencyKey = idempotencyKey;
		this.reason = reason;
		this.status = PaymentCancelStatus.REQUESTED;
	}
	public static PaymentCancel create(Long paymentId, Long cancelAmount, String idempotencyKey, String reason) {
		return new PaymentCancel(paymentId, cancelAmount, idempotencyKey, reason);
	}

	public void success(String pgCancelTransactionId, LocalDateTime canceledAt) {
		this.status = PaymentCancelStatus.SUCCESS;
		this.pgCancelTransactionId = pgCancelTransactionId;
		this.canceledAt = canceledAt;
	}
	public void fail(String failReason) {
		this.status = PaymentCancelStatus.FAIL;
		this.failReason = failReason;
	}
}
