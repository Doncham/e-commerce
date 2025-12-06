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

	private Point(Long userId, Long balance) {
		this.userId = userId;
		this.balance = balance;
	}
	public static Point createPoint(Long userId) {
		return new Point(userId, 0L);
	}

	public Long increaseBalance(Long earnedPoint) {
		this.balance += earnedPoint;
		return this.balance;
	}
}
