package kr.hhplus.be.server.application.product;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PopularProductIncrementPayload {
	// 멱등/추적용
	private Long orderId;

	// "20260209" 같은 문자열 (Asia/Seoul 기준)
	private String yyyymmdd;

	// 판매수량 기준 증분
	private List<Item> items;

	@Getter
	@NoArgsConstructor
	@AllArgsConstructor
	public static class Item {
		private Long productId;
		private Long qty;
	}
}
