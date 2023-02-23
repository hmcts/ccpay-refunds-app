package uk.gov.hmcts.reform.refunds.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserIdResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentGroupResponse;
import uk.gov.hmcts.reform.refunds.exceptions.ForbiddenToApproveRefundException;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundReviewRequestException;
import uk.gov.hmcts.reform.refunds.mappers.RefundReviewMapper;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;
import uk.gov.hmcts.reform.refunds.state.RefundState;
import uk.gov.hmcts.reform.refunds.utils.RefundServiceRoleUtil;
import uk.gov.hmcts.reform.refunds.utils.RefundsUtil;
import uk.gov.hmcts.reform.refunds.utils.StateUtil;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationFlag.NOTAPPLICABLE;
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
    private RefundsService refundsService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private RefundServiceRoleUtil refundServiceRoleUtil;

    @Autowired
    private RefundsUtil refundsUtil;
    private static final String NOTES = "Refund cancelled due to payment failure";
    private static final String REFUND_CANCELLED = "Refund cancelled";
    private static final String CANCELLED = "Cancelled";
    private static final String FEE_AND_PAY = "Fee and Pay";

    private static final Logger LOG = LoggerFactory.getLogger(RefundReviewServiceImpl.class);

    @Override
    public ResponseEntity<String> reviewRefund(MultiValueMap<String, String> headers, String reference,
                                               RefundEvent refundEvent, RefundReviewRequest refundReviewRequest) {
        IdamUserIdResponse userId = idamService.getUserId(headers);

        Refund refundForGivenReference = validatedAndGetRefundForGivenReference(reference, userId.getUid());
        refundServiceRoleUtil.validateRefundRoleWithServiceName(userId.getRoles(), refundForGivenReference.getServiceType());
        LOG.info("Refund validated before further processing, RC -> {}", refundForGivenReference.getPaymentReference());

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
            LOG.info("Before Calling Payment app, RC -> {}", refundForGivenReference.getPaymentReference());
            PaymentGroupResponse paymentData = paymentService.fetchPaymentGroupResponse(
                headers,
                refundForGivenReference.getPaymentReference()
            );
            LOG.info("Response received from Payment app {}", paymentData);
            refundsUtil.logPaymentDto(paymentData);
            refundsUtil.validateRefundRequestFees(refundForGivenReference, paymentData);
            updateRefundStatus(refundForGivenReference, refundEvent);
            notificationService.updateNotification(headers, refundForGivenReference,
                                                   refundReviewRequest.getTemplatePreview());
            refundsRepository.save(refundForGivenReference);
            statusMessage = "Refund approved";
            LOG.info("APPROVED Status saved in Refunds DB");
        }

        if (refundEvent.equals(RefundEvent.REJECT)) {
            refundForGivenReference.setContactDetails(null);

            refundForGivenReference.setNotificationSentFlag(NOTAPPLICABLE.getFlag());

            updateRefundStatus(refundForGivenReference, refundEvent);
            statusMessage = "Refund rejected";
        }

        if (refundEvent.equals(RefundEvent.UPDATEREQUIRED)) {
            updateRefundStatus(refundForGivenReference, refundEvent);
            statusMessage = "Refund returned to caseworker";
        }

        return new ResponseEntity<>(statusMessage, HttpStatus.CREATED);
    }

    @Override
    public ResponseEntity<String> cancelRefunds(MultiValueMap<String, String> headers, String paymentReference) {
        List<RefundStatus> forbiddenStatus = List.of(
            RefundStatus.ACCEPTED,
            RefundStatus.REJECTED,
            RefundStatus.CANCELLED
        );
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
