package kr.hhplus.be.server.domain.user;

import kr.hhplus.be.server.domain.user.User;

public interface UserPort {
	User loadUser(Long userId);
}
