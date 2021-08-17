package uk.gov.hmcts.reform.refunds.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.requests.ReconciliationProviderRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentGroupResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.ReconciliationProviderResponse;
import uk.gov.hmcts.reform.refunds.exceptions.*;
import uk.gov.hmcts.reform.refunds.mappers.ReconciliationProviderMapper;
import uk.gov.hmcts.reform.refunds.mappers.RefundReviewMapper;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;
import uk.gov.hmcts.reform.refunds.state.RefundState;

import java.util.*;

import static uk.gov.hmcts.reform.refunds.state.RefundState.SUBMITTED;

@Service
public class RefundReviewServiceImpl implements  RefundReviewService{

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

    @Override
    public ResponseEntity<String> reviewRefund(MultiValueMap<String, String> headers, String reference, RefundEvent refundEvent, RefundReviewRequest refundReviewRequest) {
        Refund refundForGivenReference = validatedAndGetRefundForGivenReference(reference);

        String userId = idamService.getUserId(headers);
        List<StatusHistory> statusHistories = new ArrayList<>(refundForGivenReference.getStatusHistories());
        refundForGivenReference.setUpdatedBy(userId);
        statusHistories.add(StatusHistory.statusHistoryWith()
                                .createdBy(userId)
                                .status(refundReviewMapper.getStatus(refundEvent))
                                .notes(refundReviewMapper.getStatusNotes(refundEvent, refundReviewRequest))
                                .build());
        refundForGivenReference.setStatusHistories(statusHistories);

        if(refundEvent.equals(RefundEvent.APPROVE)){
                PaymentGroupResponse paymentData = paymentService.fetchPaymentGroupResponse(headers, refundForGivenReference.getPaymentReference());
                ReconciliationProviderRequest reconciliationProviderRequest = reconciliationProviderMapper.getReconciliationProviderRequest(paymentData, refundForGivenReference);
                ResponseEntity<ReconciliationProviderResponse> reconciliationProviderResponseResponse = reconciliationProviderService.updateReconciliationProviderWithApprovedRefund(headers,
                                                                                                                                                               reconciliationProviderRequest
                );
                if(reconciliationProviderResponseResponse.getStatusCode().is2xxSuccessful()){
                    updateRefundStatus(refundForGivenReference, refundEvent);
                }
        }

        if(refundEvent.equals(RefundEvent.REJECT)||refundEvent.equals(RefundEvent.SENDBACK)){
            updateRefundStatus(refundForGivenReference, refundEvent);
        }
        return new ResponseEntity<>("Refund request reviewed successfully", HttpStatus.CREATED);
    }

    private Refund validatedAndGetRefundForGivenReference(String reference){
        Refund refund = refundsService.getRefundForReference(reference);

        if(!refund.getRefundStatus().equals(SUBMITTED.getRefundStatus())){
            throw new InvalidRefundReviewRequestException("Refund is not submitted");
        }
        return refund;
    }

    /**
     * @param refund
     * @param refundEvent
     *
     * updates the refund status in database using state transition mechanism.
     * @return
     */
    private Refund updateRefundStatus(Refund refund, RefundEvent refundEvent) {
        RefundState updateStatusAfterAction = RefundState.valueOf(refund.getRefundStatus().getName().toUpperCase(Locale.getDefault()));
        // State transition logic
        RefundState newState = updateStatusAfterAction.nextState(refundEvent);
        refund.setRefundStatus(newState.getRefundStatus());
        return refundsRepository.save(refund);
    }


}
