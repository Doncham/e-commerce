package kr.hhplus.be.server.application.product;

public enum PopularDateRange {
	SEVEN(7), THIRTY(30);
	private final int days;

	PopularDateRange(int days) {
		this.days = days;
	}

	public int days() {
		return days;
	}
}
