package kr.hhplus.be.server.api.order;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.hhplus.be.server.api.order.request.OrderDraftCreateRequest;
import kr.hhplus.be.server.api.order.response.OrderCreateResponse;
import kr.hhplus.be.server.application.order.OrderFacade;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/order")
@RequiredArgsConstructor
public class OrderController {
	private final OrderFacade orderFacade;
	@PutMapping("/create")
	public ResponseEntity<OrderCreateResponse> createOrUpdateOrder(@RequestBody OrderDraftCreateRequest request) {
		OrderCreateResponse response = orderFacade.createOrUpdateOrder(request);
		return ResponseEntity.ok(response);
	}
}
