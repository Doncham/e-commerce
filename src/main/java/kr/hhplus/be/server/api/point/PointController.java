package kr.hhplus.be.server.api.point;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.hhplus.be.server.api.point.request.PointChargeRequest;
import kr.hhplus.be.server.api.point.response.PointChargeResponse;
import kr.hhplus.be.server.application.point.PointCommandService;
import kr.hhplus.be.server.application.point.PointFacade;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointController {
	private final PointFacade pointFacade;
	@PostMapping("/charge")
	public ResponseEntity<PointChargeResponse> chargePoint(@RequestBody PointChargeRequest request) {
		PointChargeResponse response = pointFacade.charge(request);
		return ResponseEntity.ok(response);
	}
}
