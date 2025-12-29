package kr.hhplus.be.server.application.common.retry;

import org.springframework.stereotype.Component;

@Component("lockRetryPolicy")
public class MySqlLockRetryPolicy {
	public boolean isMySqlLockWaitTimeout(Throwable t) {
		Throwable root = rootCause(t);

		// MySQL lock wait timeout: errorCode 1205가 가장 확실한 판별 기준
		if(root instanceof java.sql.SQLException se) {
			return se.getErrorCode() == 1205;
		}

		String msg = root != null ? String.valueOf(root.getMessage()) : "";
		return msg.contains("Lock wait timeout exceeded");
	}
	private Throwable rootCause(Throwable t) {
		Throwable cur = t;
		while (cur != null && cur.getCause() != null && cur.getCause() != cur) {
			cur = cur.getCause();
		}
		return cur;
	}
}
