package kr.hhplus.be.server.user.port.out;

import kr.hhplus.be.server.user.User;

public interface UserPort {
	User loadUser(Long userId);
}
