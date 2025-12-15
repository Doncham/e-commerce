package kr.hhplus.be.server.api.point;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.hhplus.be.server.api.point.request.PointChargeRequest;
import kr.hhplus.be.server.api.point.response.PointChargeResponse;
import kr.hhplus.be.server.application.point.PointCommandService;
import lombok.RequiredArgsConstructor;

@RestController("/api")
@RequiredArgsConstructor
public class PointController {
	private final PointCommandService pointService;
	@PostMapping("/points/charge")
	public ResponseEntity<PointChargeResponse> chargePoint(PointChargeRequest request) {
		PointChargeResponse response = pointService.charge(request);
		return ResponseEntity.ok(response);
	}
}
