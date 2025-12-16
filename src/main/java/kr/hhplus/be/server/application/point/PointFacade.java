package kr.hhplus.be.server.application.point;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import kr.hhplus.be.server.api.payment.request.PaymentGatewayRequest;
import kr.hhplus.be.server.api.payment.response.PaymentGatewayResponse;
import kr.hhplus.be.server.api.point.request.PointChargeRequest;
import kr.hhplus.be.server.api.point.response.PointChargeResponse;
import kr.hhplus.be.server.domain.payment.PaymentGatewayPort;
import kr.hhplus.be.server.domain.point.exception.PointAmountNotValidException;
import kr.hhplus.be.server.domain.point.exception.PointChargeNegativeException;
import kr.hhplus.be.server.domain.pointcharge.PointCharge;
import kr.hhplus.be.server.exception.ErrorCode;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PointFacade {
	private final PointCommandService command;
	private final PaymentGatewayPort pgPort;
	private final PointQueryService query;

	public PointChargeResponse charge(PointChargeRequest req) {
		long amount = req.getAmount();
		String idempotencyKey = req.getIdempotencyKey();
		long userId = req.getUserId();
		if(amount <= 0)
			throw new PointChargeNegativeException(ErrorCode.POINT_AMOUNT_NOT_VALID, userId, idempotencyKey);
		// 멱등 체크
		PointCharge pending;
		try {
			pending = command.createPending(req);
		} catch (DataIntegrityViolationException e) {
			return query.findChargeResult(userId, idempotencyKey);
		}

		// tx 밖에서 PG 호출
		PaymentGatewayResponse pgResp = pgPort.requestPayment(
			PaymentGatewayRequest.forPointCharge(pending.getId(), amount, idempotencyKey));

		// tx2: 결과 반영
		return command.charge(pending.getId(), req, pgResp);
	}
}
