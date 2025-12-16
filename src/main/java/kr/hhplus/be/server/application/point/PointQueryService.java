package kr.hhplus.be.server.application.point;

import org.springframework.stereotype.Service;

import kr.hhplus.be.server.api.point.response.PointChargeResponse;
import kr.hhplus.be.server.domain.pointcharge.PointCharge;
import kr.hhplus.be.server.domain.pointcharge.exception.PointChargeNotFoundException;
import kr.hhplus.be.server.exception.ErrorCode;
import kr.hhplus.be.server.infrastructure.persistence.pointcharge.PointChargeRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PointQueryService {
	private final PointChargeRepository pointChargeRepository;
	public PointChargeResponse findChargeResult(long userId, String idempotencyKey) {
		PointCharge pc = pointChargeRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
			.orElseThrow(
				() -> new PointChargeNotFoundException(ErrorCode.NOT_FOUND_POINT_CHARGE, userId, idempotencyKey));

		return PointChargeResponse.of(pc);
	}
}
