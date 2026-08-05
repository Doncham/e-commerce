package kr.hhplus.be.server.domain.point;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class Point {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true)
	private Long userId;

	@Column(nullable = false)
	private Long balance;

	@Column(nullable = false)
	private Long reserved;

	private Point(
		Long userId,
		Long balance,
		Long reserved
	) {
		this.userId = userId;
		this.balance = balance;
		this.reserved = reserved;

		validateInvariant();
	}

	public static Point createPoint(Long userId) {
		return new Point(userId, 0L, 0L);
	}

	public Long increaseBalance(Long amount) {
		validatePositiveAmount(amount);

		this.balance += amount;
		return this.balance;
	}

	public long availablePoint(){
		validateInvariant();
		return Math.max(balance - reserved, 0);
	}

	public void reservePoint(long amount) {
		validatePositiveAmount(amount);
		validateInvariant();

		if (availablePoint() < amount) {
			throw new IllegalStateException(
				"Insufficient available points. "
					+ "pointId=" + id
					+ ", available=" + availablePoint()
					+ ", requested=" + amount
			);
		}

		reserved += amount;
	}

	public void confirmUse(long amount) {
		validatePositiveAmount(amount);
		validateInvariant();

		if (reserved < amount) {
			throw new IllegalStateException(
				"Confirm amount exceeds reserved points. "
					+ "pointId=" + id
					+ ", reserved=" + reserved
					+ ", confirmAmount=" + amount
			);
		}

		if (balance < amount) {
			throw new IllegalStateException(
				"Confirm amount exceeds point balance. "
					+ "pointId=" + id
					+ ", balance=" + balance
					+ ", confirmAmount=" + amount
			);
		}

		reserved -= amount;
		balance -= amount;
	}

	public void releaseReserve(long amount) {
		validatePositiveAmount(amount);
		validateInvariant();

		if (reserved < amount) {
			throw new IllegalStateException(
				"Release amount exceeds reserved points. "
					+ "pointId=" + id
					+ ", reserved=" + reserved
					+ ", releaseAmount=" + amount
			);
		}

		reserved -= amount;
	}

	private void validateInvariant() {
		if (balance < 0
			|| reserved < 0
			|| reserved > balance) {
			throw new IllegalStateException(
				"Invalid point state. "
					+ "pointId=" + id
					+ ", balance=" + balance
					+ ", reserved=" + reserved
			);
		}
	}

	private static void validatePositiveAmount(
		long amount
	) {
		if (amount <= 0) {
			throw new IllegalArgumentException(
				"Point amount must be positive. "
					+ "amount=" + amount
			);
		}
	}
}
