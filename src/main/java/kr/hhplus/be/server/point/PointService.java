package kr.hhplus.be.server.point;

import java.math.BigDecimal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.point.exception.PointAmountNotValidException;
import kr.hhplus.be.server.pointhistory.ChangeType;
import kr.hhplus.be.server.pointhistory.PointHistory;
import kr.hhplus.be.server.pointhistory.PointHistoryRepository;
import kr.hhplus.be.server.pointhistory.SourceType;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PointService {
	private final PointRepository pointRepository;
	private final PointHistoryRepository pointHistoryRepository;
	private static final BigDecimal EARN_RATE = new BigDecimal("0.01"); // 1% 적립

	@Transactional
	public void earnForOrder(Long userId, Long orderId, Long payAmount) {
		if(payAmount == null || payAmount <= 0) {
			throw new PointAmountNotValidException(ErrorCode.POINT_AMOUNT_NOT_VALID, orderId);
		}
		Point point = pointRepository.findByUserIdForUpdate(userId).orElseGet(() ->
			pointRepository.save(Point.createPoint(userId))
		);

		Long earnedPoint = calculateEarnedPoint(payAmount);
		Long balanceAfterCharge = point.increaseBalance(earnedPoint);
		PointHistory pointHistory = PointHistory.createPointHistory(userId, ChangeType.EARN, earnedPoint,
			balanceAfterCharge, SourceType.ORDER, orderId);

		pointHistoryRepository.save(pointHistory);
	}

	private Long calculateEarnedPoint(Long payAmount) {
		return BigDecimal.valueOf(payAmount)
			.multiply(EARN_RATE)
			.setScale(0, BigDecimal.ROUND_DOWN) // 내림
			.longValue();
	}
}
