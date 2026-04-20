package kr.hhplus.be.server.config.datasource;

public class DataSourceContextHolder {
	private static final ThreadLocal<DataSourceType> CONTEXT = new ThreadLocal<>();

	public static void set(DataSourceType dataSourceType) {
		CONTEXT.set(dataSourceType);
	}

	public static DataSourceType get() {
		return CONTEXT.get();
	}
	// 스레드 로컬은 쓰고 나서 꼭 clear 해줘야 해. 다음 요청에 영향이 감.
	// try-finally로 하네
	public static void clear() {
		CONTEXT.remove();
	}
}
