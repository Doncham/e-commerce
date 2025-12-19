package kr.hhplus.be.server.domain.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "users")
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	// 최소 식별/테스트용 필드
	@Column(nullable = false, unique = true, length = 100)
	private String email;

	@Column(nullable = false, length = 50)
	private String name;

	private User(String email, String name) {
		this.email = email;
		this.name = name;
	}

	public static User create(String email, String name) {
		return new User(email, name);
	}
}
