package kr.hhplus.be.server.point;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Version;
import kr.hhplus.be.server.user.User;

@Entity
public class Point {
	@Id
	private Long id;

	@OneToOne
	@JoinColumn(name = "user_id", nullable = false, unique = true)
	private User user;

	@Version
	// 비관적 락 공부 ㄱㄱ
	private Long version;
}
