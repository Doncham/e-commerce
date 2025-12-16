package kr.hhplus.be.server.application.point;

import kr.hhplus.be.server.domain.point.Point;

public interface PointPort {
	Point findByUserIdForUpdate(Long userId);
}
