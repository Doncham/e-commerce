package kr.hhplus.be.server.application.outbox;

import static kr.hhplus.be.server.domain.outbox.OutboxEvent.*;

import java.util.ArrayList;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import kr.hhplus.be.server.domain.outbox.OutboxBusinessTxService;
import kr.hhplus.be.server.domain.outbox.OutboxEvent;
import kr.hhplus.be.server.domain.outbox.OutboxStatusService;
import kr.hhplus.be.server.infrastructure.persistence.outbox.OutboxEventRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SingleOutboxProcessService {
	private static final String UK_POINT_EARN = "uk_user_sourceType_sourceId_change_type";

	private final OutboxEventRepository outboxEventRepo;
	private final OutboxStatusService outboxStatusService;
	private final OutboxBusinessTxService outboxBusinessTxService;

	public void processOne(Long eventId) {
		try {
			// ✅ 성공 경로: 비즈니스 + outbox PROCESSED를 한 트랜잭션으로 묶어서 커밋
			outboxBusinessTxService.handleAndMarkProcessedTx(eventId);
		}
		catch (Exception e) {
			// ✅ 중복 적립(유니크 위반)은 실패가 아니라 "이미 처리됨" -> PROCESSED
			if (e instanceof DataIntegrityViolationException dive && isUniqueConstraint(dive, UK_POINT_EARN)) {
				outboxStatusService.markProcessed(eventId);
				return;
			}
			// ✅ 데드락/락 타임아웃은 재시도
			if (isRetryable(e)) {
				OutboxEvent current = outboxEventRepo.findById(eventId).orElseThrow();
				int nextCount = current.getRetryCount() + 1;

				if (nextCount <= 10) {
					outboxStatusService.markRetry(eventId, e);
				} else {
					outboxStatusService.markFailed(eventId, e);
				}
				return;
			}

			// ✅ 그 외는 실패(도메인/비재시도 예외)
			outboxStatusService.markFailed(eventId, e);
		}
	}


	private boolean isRetryable(Throwable t) {
		Throwable root = rootCause(t);
		if (root instanceof java.sql.SQLException se) {
			int code = se.getErrorCode();
			return code == 1205 /* lock wait timeout */ || code == 1213 /* deadlock */;
		}
		return false;
	}


	public boolean isUniqueConstraint(Throwable ex, String constraintName) {
		Throwable t = ex;
		while (t != null) {
			if (t instanceof ConstraintViolationException cve) {
				String name = cve.getConstraintName(); // 위반된 제약명(없으면 null)
				return name != null && name.toLowerCase().contains(constraintName.toLowerCase());
			}
			t = t.getCause();
		}
		return false;
	}

}
