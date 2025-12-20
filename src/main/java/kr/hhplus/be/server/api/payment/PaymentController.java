package kr.hhplus.be.server.api.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.hhplus.be.server.api.payment.request.PayRequest;
import kr.hhplus.be.server.api.payment.request.PayResponse;
import kr.hhplus.be.server.application.payment.PaymentFacade;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {
	private final PaymentFacade paymentFacade;

	@PostMapping("/create")
	public ResponseEntity<PayResponse> createPayment(@RequestBody PayRequest request) {
		PayResponse response = paymentFacade.pay(request);
		return ResponseEntity.ok(response);
	}

}
