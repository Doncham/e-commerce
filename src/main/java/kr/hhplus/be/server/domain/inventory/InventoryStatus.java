package kr.hhplus.be.server.domain.inventory;

public enum InventoryStatus {
	OUT_OF_STOCK, // 0
	LOW_STOCK, // 1~threshold
	IN_STOCK; // threshold+

	public static InventoryStatus from(long available, int lowThreshold) {
		if(available <= 0) return OUT_OF_STOCK;
		if(available <= lowThreshold) return LOW_STOCK;
		return IN_STOCK;
	}
}
