package kr.hhplus.be.server.domain.inventory.exception;

import kr.hhplus.be.server.exception.ErrorCode;

public class NotFoundInventoryException extends InventoryException {
	public static String Message = "인벤토리를 찾을 수 없습니다. inventoryId=";
	public NotFoundInventoryException(String inventoryId) {
		super(ErrorCode.NOT_FOUND_INVENTORY, Message + inventoryId);
	}
}
