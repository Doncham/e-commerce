package kr.hhplus.be.server.api.payment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.hhplus.be.server.api.payment.request.PayRequest;
import kr.hhplus.be.server.api.payment.request.PayResponse;
import kr.hhplus.be.server.application.payment.PaymentService;
import kr.hhplus.be.server.application.payment.PaymentFacade;
import kr.hhplus.be.server.domain.payment.dto.PaymentDetailResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {
	private final PaymentFacade paymentFacade;
	private final PaymentService paymentService;

	@PostMapping("/create")
	public ResponseEntity<PayResponse> createPayment(@RequestBody PayRequest request) {
		PayResponse response = paymentFacade.pay(request);
		return ResponseEntity.ok(response);
	}

	@GetMapping("/{paymentId}")
	public ResponseEntity<PaymentDetailResponse> getPaymentDetail(@PathVariable Long paymentId) {
		return ResponseEntity.ok(paymentService.getPaymentDetail(paymentId));
	}

}
