package kr.hhplus.be.server.payment;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import kr.hhplus.be.server.entity.BaseTimeEntity;
import kr.hhplus.be.server.order.Order;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 한 주문에 대해서 결제가 여러번 생길 수도 있을듯
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	@Column(nullable = false)
	private Long amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStatus status;

	@Column(nullable = false, unique = true)
	private String pgTransactionId;

	private LocalDateTime processedAt;

	private Payment(Order order, Long amount, PaymentStatus status, String pgTransactionId, LocalDateTime processedAt) {
		this.order = order;
		this.amount = amount;
		this.status = status;
		this.pgTransactionId = pgTransactionId;
		this.processedAt = processedAt;
	}
	public static Payment createPayment(Order order, Long amount) {
		return new Payment(order, amount, PaymentStatus.REQUESTED, null, null);
	}

	public void paymentSuccess(String pgTransactionId, LocalDateTime processedAt) {
		this.status = PaymentStatus.SUCCESS;
		this.pgTransactionId = pgTransactionId;
		this.processedAt = processedAt;
	}

	public void paymentFailed(String pgTransactionId) {
		this.pgTransactionId = pgTransactionId;
		this.status = PaymentStatus.FAILURE;
		// 결제 실패니까 처리된게 아닌가? 규칙을 정하기 나름일듯
		processedAt = null;
	}

}
