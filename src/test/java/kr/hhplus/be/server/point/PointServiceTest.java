package kr.hhplus.be.server.point;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.hhplus.be.server.user.User;
import kr.hhplus.be.server.user.UserRepository;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {
	@Mock
	private PointService pointService;
	@Mock
	private UserRepository userRepository;
	@Mock
	private User user;

	// 포인트 지급 테스트
	@Test
	void givenValidUserAndPoint_whenChargePoint_thenIncreaseBalance() {
		// given
		long userId = 1L;
		long chargeAmount = 1000L;
		// 유저가 필요하고, 결제 성공 상태 시 포인트 충전
		when(userRepository.findById(userId).get()).thenReturn(user);
		//when(user.getPoint()).thenReturn(5000L);
		// when
		//pointService.chargePoint(userId, chargeAmount);

		// then

	}
	// 포인트 지급 동시성 테스트
	@Test
	void test() {
		// given
		long userId = 1L;
		long chargeAmount = 1000L;
		when(userRepository.findById(userId).get()).thenReturn(user);
		//when(user.getPoint()).thenReturn(5000L);
		// when
		//pointService.chargePoint(userId, chargeAmount);

		// then

	}

	// 포인트 충전 실패 테스트 - 음수 충전

	// 포인트 충전 내역 생성 테스트


}