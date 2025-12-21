package kr.hhplus.be.server.domain.inventory.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class InSufficientStockException extends InventoryException {
	public static String Message = "재고가 부족합니다. productId=";
	public InSufficientStockException(ErrorCode code, String productId) {
		super(code, Message + productId);
	}
}
