package kr.hhplus.be.server.exception;

public enum ErrorCode {
	NEGATIVE_CHARGE, // 음수 포인트 충전 시도
	NOT_ENOUGH_POINT, // 포인트 부족

	NOT_FOUND_INVENTORY, // 인벤토리 없음
	NOT_SUFFICIENT_STOCK // 재고 부족
}
