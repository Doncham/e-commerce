package kr.hhplus.be.server.application.paymentCancel;

import java.time.LocalDateTime;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import kr.hhplus.be.server.domain.paymentcancel.PaymentCancel;
import kr.hhplus.be.server.infrastructure.persistence.paymentcancel.PaymentCancelRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentCancelStatusService {

	private final PaymentCancelRepository paymentCancelRepo;

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markProcessing(Long paymentCancelId) {
		update(paymentCancelId, PaymentCancel::markProcessing);
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markPgCanceledCompleted(Long paymentCancelId, String pgCancelTxId, LocalDateTime canceledAt) {
		update(paymentCancelId, paymentCancel -> paymentCancel.markPgCanceledCompleted(pgCancelTxId, canceledAt));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void failRetryableBeforePg(Long paymentCancelId, String reason) {
		update(paymentCancelId, paymentCancel -> paymentCancel.failRetryableBeforePg(reason));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void failRetryableAfterPg(Long paymentCancelId, String reason) {
		update(paymentCancelId, paymentCancel -> paymentCancel.failRetryableAfterPg(reason));
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void failPermanent(Long paymentCancelId, String reason) {
		update(paymentCancelId, paymentCancel -> paymentCancel.failPermanent(reason));
	}

	private void update(Long paymentCancelId, Consumer<PaymentCancel> updater) {
		PaymentCancel paymentCancel = paymentCancelRepo.findByIdForUpdate(paymentCancelId)
			.orElseThrow(() -> new IllegalArgumentException("paymentCancel not found. id=" + paymentCancelId));

		updater.accept(paymentCancel);
	}

}