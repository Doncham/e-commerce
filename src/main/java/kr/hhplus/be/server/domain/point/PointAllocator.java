package kr.hhplus.be.server.domain.point;

import java.util.List;

import kr.hhplus.be.server.domain.orderproduct.OrderProduct;

public class PointAllocator {
	public static void allocatePoints(List<OrderProduct> items, long totalPointUseAmount) {
		if (items == null || items.isEmpty()) {
			throw new IllegalArgumentException("orderProducts required");
		}

		// 쿠폰이 적용되면 이 부분을 수정해야함.(쿠폰이 적용된 가격을 가져와야해)
		long totalAmount = items.stream()
			.mapToLong(OrderProduct::getUnitPrice)
			.sum();

		if (totalPointUseAmount > totalAmount) {
			throw new IllegalArgumentException("포인트 사용량이 상품 가격을 초과할 수 없습니다.");
		}

		long allocatedSum = 0L;

		for (int i = 0; i < items.size(); i++) {
			OrderProduct orderProduct = items.get(i);

			long allocated;
			// 마지막 상품이 나머지 전부 가져감.
			if (i == items.size() - 1) {
				allocated = totalPointUseAmount - allocatedSum;
			} else {
				// 여기도 쿠폰 적용된 금액으로 수정 필요
				allocated = totalPointUseAmount * orderProduct.getUnitPrice() / totalAmount;
				allocatedSum += allocated;
			}
			orderProduct.allocatePoint(allocated);
		}
	}
}
