package uk.gov.hmcts.reform.refunds.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatusUpdateRequest;
import uk.gov.hmcts.reform.refunds.exceptions.ActionNotAllowedException;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.state.RefundState;
import uk.gov.hmcts.reform.refunds.utils.RefundsUtil;
import uk.gov.hmcts.reform.refunds.utils.StateUtil;

import java.util.Arrays;

@Service
public class RefundStatusServiceImpl extends StateUtil implements RefundStatusService {

    private static final String LIBERATA_NAME = "Middle office provider";
    private static final String ACCEPTED = "Accepted";
    private static final Logger LOG = LoggerFactory.getLogger(RefundStatusServiceImpl.class);

    @Autowired
    private RefundsRepository refundsRepository;

    @Autowired
    private NotificationService notificationService;

    private StatusHistory getStatusHistoryEntity(String uid, RefundStatus refundStatus, String reason) {
        return StatusHistory.statusHistoryWith()
            .createdBy(uid)
            .notes(reason)
            .status(refundStatus.getName())
            .build();
    }

    @Override
    public ResponseEntity updateRefundStatus(String reference, RefundStatusUpdateRequest statusUpdateRequest, MultiValueMap<String, String> headers) {
        LOG.info("statusUpdateRequest: {}", statusUpdateRequest);
        LOG.info("Reference -----> " + reference);

        Refund refund = refundsRepository.findByReferenceOrThrow(reference);

        LOG.info("RefundStatusService.updateRefundStatus 1 ---> " + refund.toString());
        LOG.info("RefundStatusService.updateRefundStatus 2 ---> " + refund.getRefundInstructionType());
        LOG.info("RefundStatusService.updateRefundStatus 3 ---> " + refund.getReason());
        LOG.info("RefundStatusService.updateRefundStatus 4---> " + refund.getRefundStatus().getName());

        RefundState currentRefundState = getRefundState(refund.getRefundStatus().getName());
        if (currentRefundState.getRefundStatus().equals(RefundStatus.APPROVED)) {
            if (statusUpdateRequest.getStatus().getCode().equals(ACCEPTED)) {
                refund.setRefundStatus(RefundStatus.ACCEPTED);
                refund.setStatusHistories(Arrays.asList(getStatusHistoryEntity(
                    LIBERATA_NAME,
                    RefundStatus.ACCEPTED,
                    "Approved by middle office")
                ));
            } else {
                refund.setRefundStatus(RefundStatus.REJECTED);
                refund.setStatusHistories(Arrays.asList(getStatusHistoryEntity(
                    LIBERATA_NAME,
                    RefundStatus.REJECTED,
                    statusUpdateRequest.getReason())
                ));
                refund.setReason(statusUpdateRequest.getReason());

                if (null != statusUpdateRequest.getReason()
                    && statusUpdateRequest.getReason().equalsIgnoreCase(
                        RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON)) {
                    LOG.info("RefundStatusService.updateRefundStatus 5.1 ---> " + refund.toString());
                    refund.setRefundInstructionType(RefundsUtil.REFUND_WHEN_CONTACTED);
                    LOG.info("RefundStatusService.updateRefundStatus 5.2---> " + refund.toString());
                    notificationService.updateNotification(headers,refund);
                    LOG.info("RefundStatusService.updateRefundStatus 5.3---> " + refund.toString());
                }
            }
            refund.setUpdatedBy(LIBERATA_NAME);
        } else {
            throw new ActionNotAllowedException("Action not allowed to proceed");
        }
        LOG.info("RefundStatusService.updateRefundStatus 5---> " + refund.toString());
        LOG.info("RefundStatusService.updateRefundStatus 6---> " + new ResponseEntity<>("Refund status updated successfully", HttpStatus.NO_CONTENT));
        return new ResponseEntity<>("Refund status updated successfully", HttpStatus.NO_CONTENT);
    }

}
