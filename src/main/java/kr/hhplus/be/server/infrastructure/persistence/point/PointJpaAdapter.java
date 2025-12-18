package kr.hhplus.be.server.infrastructure.persistence.point;

import org.springframework.stereotype.Repository;

import kr.hhplus.be.server.application.point.PointPort;
import kr.hhplus.be.server.domain.point.Point;
import kr.hhplus.be.server.domain.point.exception.PointNotFoundException;
import kr.hhplus.be.server.exception.ErrorCode;
import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class PointJpaAdapter implements PointPort {
	private final PointRepository pointRepository;

	@Override
	public Point findByUserIdForUpdate(Long userId) {
		return pointRepository.findByUserIdForUpdate(userId)
			.orElseThrow(() -> new PointNotFoundException(ErrorCode.NOT_FOUND_POINT, userId));
	}
}
