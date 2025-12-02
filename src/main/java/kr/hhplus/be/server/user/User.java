package kr.hhplus.be.server.user;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import kr.hhplus.be.server.point.Point;
import lombok.Getter;

@Entity
@Getter
@Table(name = "users")
public class User {
	@Id
	private Long id;

}
