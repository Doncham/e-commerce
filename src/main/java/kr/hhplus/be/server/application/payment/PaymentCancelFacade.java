package kr.hhplus.be.server.application.payment;

import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;

import kr.hhplus.be.server.application.payment.dto.PaymentCancelRequest;
import kr.hhplus.be.server.application.payment.dto.PaymentCancelResponse;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayCancelRequest;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayCancelResponse;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayPort;
import kr.hhplus.be.server.application.payment.pg.PaymentGatewayRouter;
import kr.hhplus.be.server.application.payment.pg.exception.PaymentGatewayRejectedException;
import kr.hhplus.be.server.application.payment.pg.exception.PaymentGatewayTemporaryException;
import kr.hhplus.be.server.application.paymentCancel.PaymentCancelStatusService;
import kr.hhplus.be.server.domain.payment.PaymentGatewayStatus;
import kr.hhplus.be.server.domain.payment.dto.PaymentCancelJob;
import kr.hhplus.be.server.domain.paymentcancel.exception.PaymentCancelPermanentException;
import kr.hhplus.be.server.domain.paymentcancel.exception.PaymentCancelProcessingException;
import kr.hhplus.be.server.domain.paymentcancel.exception.PaymentCancelTemporaryException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PaymentCancelFacade {

	private final PaymentService paymentService;
	private final PaymentGatewayRouter paymentGatewayRouter;
	private final PaymentCancelStatusService paymentCancelStatusService;

	public PaymentCancelResponse cancelPayment(PaymentCancelRequest request) {
		PaymentCancelJob job = paymentService.prepareOrGetCancelJob(request);

		if (job.isSucceeded()) {
			return paymentService.buildSucceededCancelResponse(job.getPaymentCancelId());
		}

		if (job.isProcessing()) {
			throw new PaymentCancelProcessingException("동일한 취소 요청이 이미 처리 중입니다.");
		}

		if (job.isPermanentFailed()) {
			throw new PaymentCancelPermanentException(job.getFailReason());
		}

		startProcessing(job.getPaymentCancelId());
		ensurePgCanceled(job);

		return completeInternalCancel(job.getPaymentCancelId());
	}

	// 이거 왜 try-catch까지 하는거지? 예외 변환하려고
	private void startProcessing(Long paymentCancelId) {
		try {
			paymentCancelStatusService.markProcessing(paymentCancelId);
		} catch (IllegalStateException e) {
			throw new PaymentCancelProcessingException("동일한 취소 요청이 이미 처리 중입니다.");
		}
	}

	private void ensurePgCanceled(PaymentCancelJob job) {
		if (job.isPgCancelCompleted()) {
			return;
		}

		PaymentGatewayCancelResponse response = executePgCancel(job);

		paymentCancelStatusService.markPgCanceledCompleted(
			job.getPaymentCancelId(),
			response.getPgCancelTransactionId(),
			response.getCanceledAt()
		);
	}

	private PaymentGatewayCancelResponse executePgCancel(PaymentCancelJob job) {
		try {
			PaymentGatewayPort gateway = paymentGatewayRouter.route(job.getGatewayType());

			PaymentGatewayCancelResponse response =
				gateway.cancel(PaymentGatewayCancelRequest.from(job));

			if (response.getStatus() != PaymentGatewayStatus.SUCCESS) {
				throw new PaymentGatewayTemporaryException(
					response.getFailureMessage() != null
						? response.getFailureMessage()
						: "PG cancel failed"
				);
			}

			return response;

		} catch (PaymentGatewayRejectedException e) {
			paymentCancelStatusService.failPermanent(job.getPaymentCancelId(), e.getMessage());
			throw new PaymentCancelPermanentException(e.getMessage());

		} catch (PaymentGatewayTemporaryException e) {
			paymentCancelStatusService.failRetryableBeforePg(job.getPaymentCancelId(), e.getMessage());
			throw new PaymentCancelTemporaryException(e.getMessage());
		}
	}

	private PaymentCancelResponse completeInternalCancel(Long paymentCancelId) {
		try {
			return paymentService.completeCancelPayment(paymentCancelId);

		} catch (PaymentCancelPermanentException e) {
			paymentCancelStatusService.failPermanent(paymentCancelId, e.getMessage());
			throw e;

		} catch (TransientDataAccessException e) {
			paymentCancelStatusService.failRetryableAfterPg(paymentCancelId, e.getMessage());
			throw new PaymentCancelTemporaryException(
				"PG 취소는 완료되었고 내부 반영 재시도가 필요합니다. " + e.getMessage()
			);

		} catch (PaymentCancelTemporaryException e) {
			paymentCancelStatusService.failRetryableAfterPg(paymentCancelId, e.getMessage());
			throw e;
		} catch (RuntimeException e) {
			paymentCancelStatusService.failRetryableAfterPg(
				paymentCancelId,
				"unexpected error after PG cancel: " + e.getMessage()
			);
			throw e;
		}
	}
}