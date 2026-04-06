package kr.hhplus.be.server.domain.paymentcancel;

import static org.assertj.core.api.AssertionsForClassTypes.*;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PaymentCancelTest {

	@Test
	@DisplayName("markProcessing: REQUESTED 상태에서 PROCESSING으로 변경된다")
	void markProcessing_fromRequested_toProcessing() {
		PaymentCancel paymentCancel = createPaymentCancel();

		paymentCancel.markProcessing();

		assertThat(paymentCancel.getStatus()).isEqualTo(PaymentCancelStatus.PROCESSING);
	}

	@Test
	@DisplayName("markPgCanceledCompleted: PROCESSING 상태에서 PG 취소 완료 정보가 기록된다")
	void markPgCanceledCompleted_updatesPgFields() {
		PaymentCancel paymentCancel = createPaymentCancel();
		LocalDateTime canceledAt = LocalDateTime.of(2026, 4, 2, 13, 0);

		paymentCancel.markProcessing();
		paymentCancel.markPgCanceledCompleted("pg-cancel-tx-1", canceledAt);

		assertThat(paymentCancel.isPgCancelCompleted()).isTrue();
		assertThat(paymentCancel.getPgCancellationId()).isEqualTo("pg-cancel-tx-1");
		assertThat(paymentCancel.getCanceledAt()).isEqualTo(canceledAt);
		assertThat(paymentCancel.getFailurePhase()).isEqualTo(PaymentCancelFailurePhase.AFTER_PG);
		assertThat(paymentCancel.getFailReason()).isNull();
		assertThat(paymentCancel.getStatus()).isEqualTo(PaymentCancelStatus.PROCESSING);
	}

	@Test
	@DisplayName("failRetryableBeforePg / failRetryableAfterPg: PG 완료 여부에 따라 허용 상태가 다르다")
	void failRetryable_respectsPgCompletionState() {
		PaymentCancel beforePg = createPaymentCancel();
		beforePg.markProcessing();

		beforePg.failRetryableBeforePg("network timeout");

		assertThat(beforePg.getStatus()).isEqualTo(PaymentCancelStatus.FAILED_RETRYABLE);
		assertThat(beforePg.getFailurePhase()).isEqualTo(PaymentCancelFailurePhase.BEFORE_PG);
		assertThat(beforePg.getFailReason()).isEqualTo("network timeout");

		PaymentCancel afterPg = createPaymentCancel();
		afterPg.markProcessing();
		afterPg.markPgCanceledCompleted("pg-cancel-tx-2", LocalDateTime.of(2026, 4, 2, 13, 5));

		afterPg.failRetryableAfterPg("db transient error");

		assertThat(afterPg.getStatus()).isEqualTo(PaymentCancelStatus.FAILED_RETRYABLE);
		assertThat(afterPg.getFailurePhase()).isEqualTo(PaymentCancelFailurePhase.AFTER_PG);
		assertThat(afterPg.getFailReason()).isEqualTo("db transient error");
	}

	private PaymentCancel createPaymentCancel() {
		return PaymentCancel.create(
			1L,
			10000L,
			"idem-1",
			"단순 변심",
			PaymentCancel.createFingerprint(1L, List.of(10L, 20L)),
			"10,20"
		);
	}
}