package kr.hhplus.be.server.api.payment.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayType;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaymentPrepareRequest {
	@NotNull
	private Long userId;

	@NotNull
	private Long orderId;

	@NotNull
	@PositiveOrZero
	private Long pointUseAmount;

	@NotNull
	@Valid
	List<CouponSelectionRequest> couponSelectionRequestList;

	@NotNull
	private PaymentGatewayType gatewayType;

	@Builder
	public PaymentPrepareRequest (
		Long orderId,
		Long pointUseAmount,
		List<CouponSelectionRequest> couponSelectionList,
		PaymentGatewayType gatewayType
	) {
		this.orderId = orderId;
		this.pointUseAmount = pointUseAmount;
		this.couponSelectionRequestList = couponSelectionList;
		this.gatewayType = gatewayType;
	}

}
