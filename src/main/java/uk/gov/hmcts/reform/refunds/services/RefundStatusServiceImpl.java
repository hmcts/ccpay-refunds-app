package uk.gov.hmcts.reform.refunds.services;

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
import uk.gov.hmcts.reform.refunds.utils.StateUtil;

import java.util.Arrays;

@Service
public class RefundStatusServiceImpl extends StateUtil implements RefundStatusService {

    private static final String MIDDLE_OFFICE_PROVIDER = "Middle office provider";
    @Autowired
    private RefundsRepository refundsRepository;

    private StatusHistory getStatusHistoryEntity(String uid, RefundStatus refundStatus, String reason) {
        return StatusHistory.statusHistoryWith()
            .createdBy(uid)
            .notes(reason)
            .status(refundStatus.getName())
            .build();
    }

    @Override
    public ResponseEntity updateRefundStatus(String reference, RefundStatusUpdateRequest statusUpdateRequest, MultiValueMap<String, String> headers) {
        Refund refund = refundsRepository.findByReferenceOrThrow(reference);
        RefundState currentRefundState = getRefundState(refund.getRefundStatus().getName());
        if (currentRefundState.getRefundStatus().getName().equals("sent to middle office")) {
            if (statusUpdateRequest.getStatus().getCode().equals("accepted")) {
                refund.setRefundStatus(RefundStatus.ACCEPTED);
                refund.setStatusHistories(Arrays.asList(getStatusHistoryEntity(
                    MIDDLE_OFFICE_PROVIDER,
                    RefundStatus.ACCEPTED,
                    "Approved by middle office"
                                                        )
                ));
            } else {
                refund.setRefundStatus(RefundStatus.REJECTED);
                refund.setStatusHistories(Arrays.asList(getStatusHistoryEntity(
                    MIDDLE_OFFICE_PROVIDER,
                    RefundStatus.REJECTED,
                    statusUpdateRequest.getReason()
                                                        )
                ));
            }
            refund.setUpdatedBy(MIDDLE_OFFICE_PROVIDER);
            refund.setReason(statusUpdateRequest.getReason());
        } else {
            throw new ActionNotAllowedException("Action not allowed to proceed");
        }
        return new ResponseEntity<>("Refund status updated successfully", HttpStatus.NO_CONTENT);
    }

}
