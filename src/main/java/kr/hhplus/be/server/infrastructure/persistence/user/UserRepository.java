package kr.hhplus.be.server.infrastructure.persistence.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.domain.user.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
}
