package kr.hhplus.be.server.infrastructure.persistence.user;

import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.domain.user.User;
import kr.hhplus.be.server.domain.user.UserPort;
import lombok.RequiredArgsConstructor;
@Repository
@RequiredArgsConstructor
public class UserJpaAdapter implements UserPort {
	private final UserRepository userRepository;
	@Override
	public User loadUser(Long userId) {
		return userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("Invalid user ID"));
	}
}
