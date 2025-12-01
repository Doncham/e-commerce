package kr.hhplus.be.server.user.adapter;

import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.user.User;
import kr.hhplus.be.server.user.UserRepository;
import kr.hhplus.be.server.user.port.out.UserPort;
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
