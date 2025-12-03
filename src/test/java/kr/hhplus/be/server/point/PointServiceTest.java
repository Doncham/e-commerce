package kr.hhplus.be.server.point;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import kr.hhplus.be.server.point.exception.PointAmountNotValidException;
import kr.hhplus.be.server.pointhistory.ChangeType;
import kr.hhplus.be.server.pointhistory.PointHistory;
import kr.hhplus.be.server.pointhistory.PointHistoryRepository;
import kr.hhplus.be.server.pointhistory.SourceType;

@ExtendWith(MockitoExtension.class)
class PointServiceTest {
	@InjectMocks
	private PointService pointService;
	@Mock
	private PointRepository pointRepository;
	@Mock
	private PointHistoryRepository pointHistoryRepository;

	// 포인트 지급 테스트
	@Test
	void givenValidUserAndPoint_whenChargePoint_thenIncreaseBalance() {
		// given
		long userId = 1L;
		long orderAmount = 10000L;
		long orderId = 2L;
		long balance = 2000L;
		Point point = Point.createPoint(userId);
		// 기존 포인트 초기화
		point.increaseBalance(balance);

		// 포인트 있는지 확인해서 없으면 만들어줌.
		when(pointRepository.findByUserIdForUpdate(userId)).thenReturn(Optional.of(point));

		// when
		pointService.earnForOrder(userId, orderId, orderAmount);
		ArgumentCaptor<PointHistory> pointHistoryCaptor = ArgumentCaptor.forClass(PointHistory.class);

		// then
		verify(pointHistoryRepository).save(pointHistoryCaptor.capture());
		PointHistory savedPointHistory = pointHistoryCaptor.getValue();
		assertEquals(ChangeType.EARN, savedPointHistory.getChangeType());
		assertEquals(balance + orderAmount / 100, savedPointHistory.getBalanceAfterChange()); // 1% 적립
		assertEquals(SourceType.ORDER, savedPointHistory.getSourceType());
	}
	// 포인트 충전 실패 테스트 - 음수 충전
	@Test
	void givenNegativePointAmount_whenChargePoint_thenThrowException() {
		// given
		long userId = 1L;
		long orderAmount = -5000L; // 음수 금액
		long orderId = 2L;

		// when & then
		Assertions.assertThrows(PointAmountNotValidException.class, () -> {
			pointService.earnForOrder(userId, orderId, orderAmount);
		});
	}
}