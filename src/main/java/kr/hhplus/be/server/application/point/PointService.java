package kr.hhplus.be.server.application.point;

import java.math.BigDecimal;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.api.payment.request.PaymentGatewayRequest;
import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;
import kr.hhplus.be.server.api.point.request.PointChargeRequest;
import kr.hhplus.be.server.api.point.response.PointChargeResponse;
import kr.hhplus.be.server.domain.payment.PaymentGatewayPort;
import kr.hhplus.be.server.domain.payment.PaymentGatewayStatus;
import kr.hhplus.be.server.domain.point.Point;
import kr.hhplus.be.server.domain.point.PointRepository;
import kr.hhplus.be.server.domain.pointcharge.PointCharge;
import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.domain.point.exception.PointAmountNotValidException;
import kr.hhplus.be.server.domain.pointhistory.ChangeType;
import kr.hhplus.be.server.domain.pointhistory.PointHistory;
import kr.hhplus.be.server.domain.pointhistory.PointHistoryRepository;
import kr.hhplus.be.server.domain.pointhistory.SourceType;
import kr.hhplus.be.server.infrastructure.persistence.pointcharge.PointChargeRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PointService {
	private final PointRepository pointRepository;
	private final PointHistoryRepository pointHistoryRepository;
	private static final BigDecimal EARN_RATE = new BigDecimal("0.01"); // 1% 적립
	private final PaymentGatewayPort pgPort;
	private final PointChargeRepository pointChargeRepository;

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

	@Transactional
	public PointChargeResponse charge(PointChargeRequest request) {
		long userId = request.getUserId();
		long amount = request.getAmount();
		String idempotencyKey = request.getIdempotencyKey();
		if(amount <= 0) {
			throw new PointAmountNotValidException(ErrorCode.POINT_AMOUNT_NOT_VALID, null);
		}
		Point point = pointRepository.findByUserIdForUpdate(userId).orElseGet(() ->
			pointRepository.save(Point.createPoint(userId))
		);
		try {
			// 충전 기록용 엔티티 생성
			PointCharge pointCharge = pointChargeRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
				 .orElseGet(() -> pointChargeRepository.save(PointCharge.of(userId, amount, idempotencyKey)));
			// PG 요청
			PaymentGatewayRequest pgRequest = PaymentGatewayRequest.forPointCharge(pointCharge.getId(), amount, idempotencyKey);
			PaymentGatewayResponse pgResponse = pgPort.requestPayment(pgRequest);

			// 되게 다양한 이유로 실패할 수 있을거 같은데 PG에 대한 설계가 없어서 일단 단순 처리
			if (pgResponse.getStatus() != PaymentGatewayStatus.SUCCESS) {
				// 실패한 충전 요청에 대한 기록이 남지 않는다.
				// 추가적으로 PointCharge에는 성공/실패 등의 상태가 없다.
				throw new RuntimeException("Point charge payment failed for userId: " + userId);
			}

			Long balanceAfterCharge = point.increaseBalance(amount);

			PointHistory pointHistory = PointHistory.createPointHistory(userId, ChangeType.EARN, amount,
				balanceAfterCharge, SourceType.CHARGE, pointCharge.getId());
			pointHistoryRepository.save(pointHistory);

			return new PointChargeResponse(userId, amount, balanceAfterCharge);

		} catch (DataIntegrityViolationException e) {
			// 3) 유니크 제약 위반 = 이미 누가 같은 idempotencyKey로 요청 처리함
			PointCharge existing = pointChargeRepository
				.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
				.orElseThrow(() -> new RuntimeException("PointCharge not found after unique constraint violation", e));

			return new PointChargeResponse(
				existing.getUserId(),
				existing.getAmount(),
				existing.getAmount() + pointRepository.findByUserIdForUpdate(userId)
					.map(Point::getBalance)
					.orElse(0L)
			);
		}


	}
}
