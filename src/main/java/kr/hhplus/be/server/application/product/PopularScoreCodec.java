package kr.hhplus.be.server.application.product;

public final class PopularScoreCodec {
	public static final long SHIFT = 1_000_000L;
	public static double encode(long qty, long productId) {
		if (qty < 0) throw new IllegalArgumentException("qty must be >= 0");
		if (productId <= 0) throw new IllegalArgumentException("productId must be > 0");
		if (productId >= SHIFT) throw new IllegalArgumentException("productId must be < SHIFT");

		// productId가 작을수록 tie가 큼
		long tie = SHIFT - productId;
		long packed = qty * SHIFT + tie;

		return (double) packed;
	}

	public static double deltaForIncrement(long deltaQty) {
		if (deltaQty <= 0) throw new IllegalArgumentException("deltaQty must be > 0");
		long delta = deltaQty * SHIFT;
		return (double) delta;
	}

	// 조회 결과에서 qty만 복원하고 싶을 때
	public static long decodeQty(double score) {
		long packed = (long) score;
		return packed / SHIFT;
	}

}
