package kr.hhplus.be.server.api.order;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.hhplus.be.server.api.order.request.OrderDraftCreateRequest;
import kr.hhplus.be.server.api.order.response.OrderDraftCreateResponse;
import kr.hhplus.be.server.application.order.OrderFacade;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/order")
@RequiredArgsConstructor
public class OrderController {
	private final OrderFacade orderFacade;
	@PostMapping("/create")
	public ResponseEntity<OrderDraftCreateResponse> createOrder(@RequestBody OrderDraftCreateRequest request) {
		OrderDraftCreateResponse response = orderFacade.createOrder(request);
		return ResponseEntity.ok(response);
	}
}
