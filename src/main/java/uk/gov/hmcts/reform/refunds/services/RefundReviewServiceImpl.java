package uk.gov.hmcts.reform.refunds.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.config.toggler.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.reform.refunds.dtos.requests.ReconciliationProviderRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserIdResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentGroupResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.ReconciliationProviderResponse;
import uk.gov.hmcts.reform.refunds.exceptions.ForbiddenToApproveRefundException;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundReviewRequestException;
import uk.gov.hmcts.reform.refunds.exceptions.ReconciliationProviderServerException;
import uk.gov.hmcts.reform.refunds.mappers.ReconciliationProviderMapper;
import uk.gov.hmcts.reform.refunds.mappers.RefundReviewMapper;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;
import uk.gov.hmcts.reform.refunds.state.RefundState;
import uk.gov.hmcts.reform.refunds.utils.StateUtil;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static uk.gov.hmcts.reform.refunds.state.RefundState.SENTFORAPPROVAL;

@Service
public class RefundReviewServiceImpl extends StateUtil implements RefundReviewService {

    @Autowired
    private IdamService idamService;

    @Autowired
    private RefundsRepository refundsRepository;

    @Autowired
    private RefundReviewMapper refundReviewMapper;

    @Autowired
    private ReconciliationProviderService reconciliationProviderService;

    @Autowired
    private ReconciliationProviderMapper reconciliationProviderMapper;

    @Autowired
    private RefundsService refundsService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private LaunchDarklyFeatureToggler featureToggler;

    private static final String NOTES = "Refund cancelled due to payment failure";
    private static final String REFUND_CANCELLED = "Refund cancelled";
    private static final String CANCELLED = "Cancelled";
    private static final String FEE_AND_PAY = "Fee and Pay";

    @Override
    public ResponseEntity<String> reviewRefund(MultiValueMap<String, String> headers, String reference,
                                               RefundEvent refundEvent, RefundReviewRequest refundReviewRequest) {
        IdamUserIdResponse userId = idamService.getUserId(headers);

        Refund refundForGivenReference = validatedAndGetRefundForGivenReference(reference,userId.getUid());

        List<StatusHistory> statusHistories = new ArrayList<>(refundForGivenReference.getStatusHistories());
        refundForGivenReference.setUpdatedBy(userId.getUid());
        statusHistories.add(StatusHistory.statusHistoryWith()
                                .createdBy(userId.getUid())
                                .status(refundReviewMapper.getStatus(refundEvent))
                                .notes(refundReviewMapper.getStatusNotes(refundEvent, refundReviewRequest))
                                .build());
        refundForGivenReference.setStatusHistories(statusHistories);
        String statusMessage = "";
        if (refundEvent.equals(RefundEvent.APPROVE)) {
            boolean isRefundLiberata = this.featureToggler.getBooleanValue("refund-liberata", false);
            if (isRefundLiberata) {
                PaymentGroupResponse paymentData = paymentService.fetchPaymentGroupResponse(
                    headers,
                    refundForGivenReference.getPaymentReference()
                );
                ReconciliationProviderRequest reconciliationProviderRequest = reconciliationProviderMapper.getReconciliationProviderRequest(
                    paymentData,
                    refundForGivenReference
                );
                ResponseEntity<ReconciliationProviderResponse> reconciliationProviderResponseResponse = reconciliationProviderService
                    .updateReconciliationProviderWithApprovedRefund(
                    reconciliationProviderRequest
                );
                if (reconciliationProviderResponseResponse.getStatusCode().is2xxSuccessful()) {
                    updateRefundStatus(refundForGivenReference, refundEvent);
                } else {
                    throw new ReconciliationProviderServerException("Reconciliation provider unavailable. Please try again later.");
                }
            } else {
                updateRefundStatus(refundForGivenReference, refundEvent);
            }
            statusMessage = "Refund approved";
        }

        if (refundEvent.equals(RefundEvent.REJECT) || refundEvent.equals(RefundEvent.UPDATEREQUIRED)) {
            updateRefundStatus(refundForGivenReference, refundEvent);
            statusMessage = refundEvent.equals(RefundEvent.REJECT) ? "Refund rejected" : "Refund returned to caseworker";
        }
        return new ResponseEntity<>(statusMessage, HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<String> cancelRefunds(MultiValueMap<String, String> headers, String paymentReference) {
        List<RefundStatus> forbiddenStatus = List.of(RefundStatus.ACCEPTED, RefundStatus.REJECTED, RefundStatus.CANCELLED);
        List<Refund> refundList = refundsService.getRefundsForPaymentReference(paymentReference);
        List<StatusHistory> statusHistories = new LinkedList<>();
        for (Refund refund : refundList) {
            if (!forbiddenStatus.contains(refund.getRefundStatus())) {
                statusHistories.addAll(refund.getStatusHistories());
                refund.setUpdatedBy(FEE_AND_PAY);
                statusHistories.add(StatusHistory.statusHistoryWith()
                        .createdBy(FEE_AND_PAY)
                        .status(CANCELLED)
                        .notes(NOTES)
                        .build());
                refund.setStatusHistories(statusHistories);
                updateRefundStatus(refund, RefundEvent.CANCEL);
            }
        }
        return new ResponseEntity<>(REFUND_CANCELLED, HttpStatus.OK);
    }

    private Refund validatedAndGetRefundForGivenReference(String reference, String userId) {
        Refund refund = refundsService.getRefundForReference(reference);

        if (refund.getUpdatedBy().equals(userId)) {
            throw new ForbiddenToApproveRefundException("User cannot approve this refund.");
        }

        if (!refund.getRefundStatus().equals(SENTFORAPPROVAL.getRefundStatus())) {
            throw new InvalidRefundReviewRequestException("Refund is not submitted");
        }
        return refund;
    }

    private Refund updateRefundStatus(Refund refund, RefundEvent refundEvent) {
        RefundState updateStatusAfterAction = getRefundState(refund.getRefundStatus().getName());
        // State transition logic
        RefundState newState = updateStatusAfterAction.nextState(refundEvent);
        refund.setRefundStatus(newState.getRefundStatus());
        return refundsRepository.save(refund);
    }


}
