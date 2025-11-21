package kr.hhplus.be.server.point;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PointService {
	@Transactional
	public void earn(Long userId, Long point, String reason) {

	}
}
