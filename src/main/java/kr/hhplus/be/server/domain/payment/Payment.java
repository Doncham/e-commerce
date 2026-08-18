package kr.hhplus.be.server.domain.payment;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayType;
import kr.hhplus.be.server.domain.order.Order;
import kr.hhplus.be.server.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
	uniqueConstraints = {
		// 와 이거는 유니크 제약이 아니라 인덱스를 만들고 싶었던건데 엄청난 실수..
		// @UniqueConstraint(
		// 	name = "uk_payment_paid_at",
		// 	columnNames = "paid_at"
		// ),
		@UniqueConstraint(
			name = "ux_payment_pg_order_id",
			columnNames = "pg_order_id"
		),
		@UniqueConstraint(
			name = "ux_payment_payment_key",
			columnNames = "payment_key"
		),
		@UniqueConstraint(
			name = "ux_payment_idempotency_key",
			columnNames = "idempotency_key"
		),
		@UniqueConstraint(
			name = "ux_payment_order_id",
			columnNames = "order_id"
		)
	}
)
public class Payment extends BaseTimeEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	// pg에 실제로 요청한 금액
	@Column(nullable = false)
	private Long amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentStatus status;

	// pg에 전달하는 주문 식별자.
	// 새로운 논리적 결제 준비마다 새로 생성 -> 변경 가능한 값
	@Column(name = "pg_order_id", nullable = false)
	private String pgOrderId;

	// PG 인증 성공 후 전달받는 값
	// READY에서는 null, CONFIRMING 진입 시 받음.
	@Column(name = "payment_key", unique = true)
	private String paymentKey;

	@Column(name = "paid_at")
	private LocalDateTime paidAt;

	// READY 상태의 유효기간
	@Column(name = "expires_at", nullable = false)
	private LocalDateTime expiresAt;

	// /payment/prepare 요청 멱등키
	@Column(name = "idempotency_key", nullable = false)
	private String idempotencyKey;

	private String failReason;

	@Column(nullable = false)
	private Long canceledAmount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private PaymentGatewayType gatewayType;

	private Payment(
		Order order,
		Long amount,
		PaymentGatewayType gatewayType,
		String idemKey,
		LocalDateTime expiresAt
	) {
		validatePrepareValues(
			order,
			amount,
			gatewayType,
			idemKey,
			expiresAt
		);

		this.order = order;
		this.amount = amount;
		this.gatewayType = gatewayType;
		this.idempotencyKey = idemKey;
		this.expiresAt = expiresAt;

		this.pgOrderId = generatePgOrderId();

		this.status = PaymentStatus.READY;
		this.canceledAmount = 0L;

		this.paymentKey = null;
		this.paidAt = null;
		this.failReason = null;
	}

	// 최초 결제 준비
	public static Payment createReady(
		Order order,
		long amount,
		PaymentGatewayType gatewayType,
		String idempotencyKey,
		LocalDateTime expiresAt
	) {
		return new Payment(
			order,
			amount,
			gatewayType,
			idempotencyKey,
			expiresAt
		);
	}

	private static String generatePgOrderId() {
		return "PAY_" + UUID.randomUUID();
	}

	// PG 승인 + 로컬 결제가 완료 처리된 경우
	// CONFIRMING -> SUCCESS(멱등처리)
	public void paymentSuccess(LocalDateTime paidAt) {
		if (status == PaymentStatus.SUCCESS) {
			return;
		}

		if (status != PaymentStatus.CONFIRMING) {
			throw new IllegalStateException(
				"Payment cannot succeed. "
					+ "paymentId=" + id
					+ ", status=" + status
			);
		}

		this.status = PaymentStatus.SUCCESS;
		this.paidAt = paidAt;
		this.failReason = null;
	}

	// PG가 명확한 승인 실패를 반환한 경우
	// CONFIRMING -> FAILED
	// 타임아웃처럼 성공 여부를 모르는 경우에는 호출하면 안된다.
	// 그 경우 CONFIRMING을 유지하고 PG 상태를 조회해야한다.
	public void paymentFailed (String reason) {
		if(this.status != PaymentStatus.CONFIRMING) {
			throw new IllegalArgumentException("paymentStatus가 CONFIRMING이 아닙니다. paymentId = " + this.id);
		}
		this.status = PaymentStatus.FAILED;
		// 결제 실패니까 처리된게 아닌가? 규칙을 정하기 나름일듯
		paidAt = null;
		this.failReason = reason;
	}

	public boolean canStartCancel() {
		return this.status == PaymentStatus.SUCCESS || this.status == PaymentStatus.PARTIAL_CANCELED;
	}

	public void applyCancel(Long cancelAmount) {
		if (this.status != PaymentStatus.SUCCESS && this.status != PaymentStatus.PARTIAL_CANCELED) {
			throw new IllegalStateException("paymentStatus가 SUCCESS or PARTIAL_CANCELED가 아닙니다. paymentId = " + this.id);
		}

		if (cancelAmount <= 0) {
			throw new IllegalArgumentException("cancelAmount must be positive");
		}
		long nextCanceledAmount = this.canceledAmount + cancelAmount;
		if (nextCanceledAmount > this.amount) {
			throw new IllegalArgumentException("cancelAmount exceeds remaining amount");
		}

		this.canceledAmount = nextCanceledAmount;

		if (this.canceledAmount.equals(this.amount)) {
			cancel();
		} else {
			partialCancel();
		}
	}

	private void cancel() {
		this.status = PaymentStatus.CANCELLED;
	}

	private void partialCancel() {
		this.status = PaymentStatus.PARTIAL_CANCELED;
	}

	// 주문 상품/수량 변경으로 현재 결제 준비를 무효화
	// READY -> RESET
	// 상위 서비스에서는 쿠폰,포인트,재고도 예약 해제한다.
	public void resetForOrderChange() {
		// 멱등적
		if (status == PaymentStatus.RESET) {
			return;
		}

		if (status != PaymentStatus.READY) {
			throw new IllegalStateException(
				"Only READY payment can be reset. "
					+ "paymentId=" + id
					+ ", status=" + status
			);
		}

		this.status = PaymentStatus.RESET;
	}


	// 같은 Order에서 새로운 논리적 결제 준비를 시작한다.
	// READY -> 포인트/쿠폰을 변경하고 다시 결제하기
	// FAILED -> PG 승인 실패가 확정된 뒤 재결제
	// EXPIRED -> 기존 결제 준비가 만료된 뒤 재결제
	// CONFIRMING/SUCCESS/취소 상태에서는 절대 덮어쓰지 않는다.
	public void prepareAgain(
		long amount,
		PaymentGatewayType gatewayType,
		String idempotencyKey,
		LocalDateTime expiresAt
	) {
		validatePrepareAgain();

		validatePrepareValues(
			order,
			amount,
			gatewayType,
			idempotencyKey,
			expiresAt
		);
		// 새로운 논리적 결제 준비 -> pgOrderId 새로 만들기
		this.pgOrderId = generatePgOrderId();

		this.amount = amount;
		this.idempotencyKey = idempotencyKey;
		this.gatewayType = gatewayType;
		this.expiresAt = expiresAt;

		// 이전 결제 시도에 종속되는 값을 초기화
		this.paymentKey = null;
		this.failReason = null;
		this.paidAt = null;

		this.status = PaymentStatus.READY;
	}

	public void startConfirm(String paymentKey) {
		if (status != PaymentStatus.READY) {
			throw new IllegalStateException(
				"Only READY payment can start confirmation. "
					+ "paymentId=" + id
					+ ", status=" + status
			);
		}

		this.paymentKey = Objects.requireNonNull(paymentKey);
		this.status = PaymentStatus.CONFIRMING;
	}

	public boolean hasSameIdempotencyKey(String idempotencyKey) {
		return Objects.equals(this.idempotencyKey, idempotencyKey);
	}

	public boolean isSuccess() {
		return this.status.equals(PaymentStatus.SUCCESS);
	}

	public boolean isPartialCanceled() {
		return this.status.equals(PaymentStatus.PARTIAL_CANCELED);
	}

	public boolean isCanceled() {
		return this.status.equals(PaymentStatus.CANCELLED);
	}
	public boolean isConfirming() {
		return this.status.equals(PaymentStatus.CONFIRMING);
	}

	public boolean isReady() {
		return this.status.equals(PaymentStatus.READY);
	}

	public boolean isReset() {
		return this.status.equals(PaymentStatus.RESET);
	}

	public boolean isExpired() {
		return this.status.equals(PaymentStatus.EXPIRED);
	}

	// 사용자가 결제를 진행하지 않아 READY 유효기간이 지남.
	// READY -> EXPIRED
	public void expire() {
		// 멱등적
		if (status == PaymentStatus.EXPIRED) {
			return;
		}

		if (status != PaymentStatus.READY) {
			throw new IllegalStateException(
				"Only READY payment can expire. "
					+ "paymentId=" + id
					+ ", status=" + status
			);
		}

		this.status = PaymentStatus.EXPIRED;
	}

	// 스케줄러가 만료 여부를 판단
	public boolean isExpiredAt(LocalDateTime now) {
		Objects.requireNonNull(now);

		return status == PaymentStatus.READY
			&& !expiresAt.isAfter(now);
	}

	private void validatePrepareAgain() {
		// 현재 재준비를 허용하는 상태
		// READY, RESET, FAILED, EXPIRED
		if (status == PaymentStatus.READY
			|| status == PaymentStatus.RESET
			|| status == PaymentStatus.FAILED
			|| status == PaymentStatus.EXPIRED) {
			return;
		}

		throw new IllegalStateException(
			"Payment cannot be prepared again. "
				+ "paymentId=" + id
				+ ", status=" + status
		);
	}

	private static void validatePrepareValues(
		Order order,
		long amount,
		PaymentGatewayType gatewayType,
		String idempotencyKey,
		LocalDateTime expiresAt
	) {
		Objects.requireNonNull(order, "order must not be null.");

		Objects.requireNonNull(gatewayType, "gatewayType must not be null.");

		Objects.requireNonNull(expiresAt, "expiresAt must not be null.");

		if (amount < 0) {
			throw new IllegalArgumentException(
				"Payment amount cannot be negative. "
					+ "amount=" + amount
			);
		}

		if (idempotencyKey == null
			|| idempotencyKey.isBlank()) {

			throw new IllegalArgumentException(
				"idempotencyKey must not be blank."
			);
		}
	}

}
