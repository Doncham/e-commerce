package kr.hhplus.be.server.application.point;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import kr.hhplus.be.server.api.point.request.PointChargeRequest;
import kr.hhplus.be.server.api.point.response.PointChargeResponse;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PointFacade {
	private final PointCommandService command;
	private final PointQueryService query;

	public PointChargeResponse charge(PointChargeRequest req) {
		try {
			return command.charge(req);
		} catch (DataIntegrityViolationException e) {
			return query.findChargeResult(req.getUserId(), req.getIdempotencyKey());
		}
	}
}
