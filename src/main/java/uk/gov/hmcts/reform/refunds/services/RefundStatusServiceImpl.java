package uk.gov.hmcts.reform.refunds.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundStatusUpdateRequest;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.utils.RefundsUtil;
import uk.gov.hmcts.reform.refunds.utils.StateUtil;

import java.util.Arrays;

@Service
public class RefundStatusServiceImpl extends StateUtil implements RefundStatusService {

    private static final String LIBERATA_NAME = "Middle office provider";
    private static final String ACCEPTED = "Accepted";

    private static final String LIBERATA_REASON = "Sent to Middle Office for Processing";

    private static final String SYSTEM_USER = "System user";

    private static final String LIBERATA_REJECT_UPDATE = "Refund approved by system";
    private static final Logger LOG = LoggerFactory.getLogger(RefundStatusServiceImpl.class);

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
        LOG.info("statusUpdateRequest: {}", statusUpdateRequest);

        Refund refund = refundsRepository.findByReferenceOrThrow(reference);

        if (statusUpdateRequest.getStatus().getCode().equals(ACCEPTED)) {
            refund.setRefundStatus(RefundStatus.ACCEPTED);
            refund.setStatusHistories(Arrays.asList(getStatusHistoryEntity(
                LIBERATA_NAME,
                RefundStatus.ACCEPTED,
                LIBERATA_REASON)
            ));
        } else {
            refund.setRefundStatus(RefundStatus.REJECTED);
            refund.setStatusHistories(Arrays.asList(getStatusHistoryEntity(
                LIBERATA_NAME,
                RefundStatus.REJECTED,
                statusUpdateRequest.getReason())
            ));
            refund.setUpdatedBy(LIBERATA_NAME);

        }
        return new ResponseEntity<>("Refund status updated successfully", HttpStatus.NO_CONTENT);
    }
}
