package kr.hhplus.be.server.application.point;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;
import kr.hhplus.be.server.api.point.request.PointChargeRequest;
import kr.hhplus.be.server.api.point.response.PointChargeResponse;
import kr.hhplus.be.server.domain.payment.PaymentGatewayStatus;
import kr.hhplus.be.server.domain.point.Point;
import kr.hhplus.be.server.domain.point.PointRepository;
import kr.hhplus.be.server.domain.point.exception.PointAmountNotValidException;
import kr.hhplus.be.server.domain.pointcharge.PointCharge;
import kr.hhplus.be.server.domain.pointcharge.exception.PointChargeNotFoundException;
import kr.hhplus.be.server.domain.pointhistory.ChangeType;
import kr.hhplus.be.server.domain.pointhistory.PointHistory;
import kr.hhplus.be.server.domain.pointhistory.PointHistoryRepository;
import kr.hhplus.be.server.domain.pointhistory.SourceType;
import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.infrastructure.persistence.pointcharge.PointChargeRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PointCommandService {
	private final PointRepository pointRepository;
	private final PointHistoryRepository pointHistoryRepository;
	private static final BigDecimal EARN_RATE = new BigDecimal("0.01"); // 1% 적립
	private final PointChargeRepository pointChargeRepository;

	// 이건 outbox에서 호출하고
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

	// 이건 Facade에서 호출하네
	@Transactional
	public PointCharge createPending(PointChargeRequest request) {
		long userId = request.getUserId();
		long amount = request.getAmount();
		String idempotencyKey = request.getIdempotencyKey();
		PointCharge pc = PointCharge.of(userId, amount, idempotencyKey);
		return pointChargeRepository.saveAndFlush(pc);
	}

	@Transactional
	public PointChargeResponse charge(Long pointChargeId, PointChargeRequest req, PaymentGatewayResponse pgResp) {
		long userId = req.getUserId();
		String idempotencyKey = req.getIdempotencyKey();
		long amount = req.getAmount();

		PointCharge pc = pointChargeRepository.findById(pointChargeId)
			.orElseThrow(
				() -> new PointChargeNotFoundException(ErrorCode.NOT_FOUND_POINT_CHARGE, userId, idempotencyKey));

		// 이미 처리된 PointCharge 라면 멱등 처리
		if(pc.isSuccess() || pc.isFailed()) {
			return PointChargeResponse.of(pc);
		}

		// 되게 다양한 이유로 실패할 수 있을거 같은데 PG에 대한 설계가 없어서 일단 단순 처리
		if (pgResp.getStatus() != PaymentGatewayStatus.SUCCESS) {
			pc.fail("pg 결제 실패");

			// PointHistory.createPointHistory()
			// 이전 로직에서는 이거 없어서 무조건 포인트 잔액이 증가함.
			return PointChargeResponse.of(pc);
		}

		Point point = pointRepository.findByUserIdForUpdate(userId).orElseGet(() ->
			pointRepository.save(Point.createPoint(userId))
		);

		Long balanceAfterCharge = point.increaseBalance(amount);
		pc.success(balanceAfterCharge);

		PointHistory pointHistory = PointHistory.createPointHistory(userId, ChangeType.CHARGE, amount,
			balanceAfterCharge, SourceType.CHARGE, pc.getId());
		pointHistoryRepository.save(pointHistory);

		return PointChargeResponse.of(pc);
	}

	private Long calculateEarnedPoint(Long payAmount) {
		return BigDecimal.valueOf(payAmount)
			.multiply(EARN_RATE)
			.setScale(0, RoundingMode.DOWN) // 내림
			.longValue();
	}
}
