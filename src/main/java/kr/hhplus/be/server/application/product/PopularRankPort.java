package kr.hhplus.be.server.application.product;

public interface PopularRankPort {
	void incrementDaily(String yyyymmdd, Long productId, long qty);
	void increment7d(Long productId, long qty);
	void increment30d(Long productId, long qty);
}
