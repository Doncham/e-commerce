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

	private Long balance;
	private Long reserved;

	private Point(Long userId, Long balance, Long reserved) {
		this.userId = userId;
		this.balance = balance;
		this.reserved = reserved;
	}
	public static Point createPoint(Long userId) {
		return new Point(userId, 0L, 0L);
	}

	public Long increaseBalance(Long earnedPoint) {
		this.balance += earnedPoint;
		return this.balance;
	}
	public long availablePoint(){ return Math.max(balance - reserved, 0);}
	public void reservePoint(Long qty) { this.reserved += qty; }

	public void confirmUse(long amount) {
		// 실무에서는 체크해서 예외 던져라
		this.reserved -= amount;
		this.balance -= amount;
	}

	public void releaseReserve(long amount) {
		this.reserved -= amount;
	}
}
