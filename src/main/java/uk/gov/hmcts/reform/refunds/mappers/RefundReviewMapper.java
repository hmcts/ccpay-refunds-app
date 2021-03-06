package uk.gov.hmcts.reform.refunds.mappers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundReviewRequest;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundReviewRequestException;
import uk.gov.hmcts.reform.refunds.model.RejectionReason;
import uk.gov.hmcts.reform.refunds.repository.RejectionReasonRepository;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;

import java.util.Optional;

@Component
public class RefundReviewMapper {

    @Autowired
    private RejectionReasonRepository rejectionReasonRepository;

    private static final String APPROVED = "Approved";
    private static final String REJECTED = "Rejected";
    private static final String UPDATEREQUIRED = "Update required";
    private static final String SENTTOMIDDLEOFFICE = "Sent to middle office";

    public String getStatus(RefundEvent refundEvent) {
        return  refundEvent.equals(RefundEvent.APPROVE) ? APPROVED : refundEvent.equals(RefundEvent.REJECT)
            ? REJECTED : UPDATEREQUIRED;
    }

    public String getStatusNotes(RefundEvent refundEvent, RefundReviewRequest refundReviewRequest) {
        String notes;
        if (refundEvent.equals(RefundEvent.APPROVE)) {
            notes =  SENTTOMIDDLEOFFICE;
        } else if (refundEvent.equals(RefundEvent.REJECT) || refundEvent.equals(RefundEvent.UPDATEREQUIRED)) {

            if (refundEvent.equals(refundEvent.REJECT)) {
                notes = getRejectStatusNotes(refundReviewRequest);
            } else {
                if (refundReviewRequest.getReason() == null || refundReviewRequest.getReason().isEmpty()) {
                    throw new InvalidRefundReviewRequestException("Enter reason for sendback");
                } else {
                    notes = refundReviewRequest.getReason();
                }
            }
        } else {
            notes = "";
        }
        return notes;
    }


    private String getRejectStatusNotes(RefundReviewRequest refundReviewRequest) {
        if (refundReviewRequest.getCode() == null || refundReviewRequest.getCode().isEmpty()) {
            throw new InvalidRefundReviewRequestException("Refund reject reason is required");
        }
        if (refundReviewRequest.getCode().equals("RE005")) {
            if (refundReviewRequest != null && (refundReviewRequest.getReason() == null || refundReviewRequest.getReason().isEmpty())) {
                throw new InvalidRefundReviewRequestException("Refund reject reason is required for others");
            } else {
                return refundReviewRequest.getReason();
            }
        } else {
            return validateRefundRejectionReason(refundReviewRequest.getCode()).getName();
        }
    }


    private RejectionReason validateRefundRejectionReason(String reasonCode) {
        Optional<RejectionReason> rejectionReasonObject = rejectionReasonRepository.findByCode(reasonCode);
        if (!rejectionReasonObject.isPresent()) {
            throw new InvalidRefundReviewRequestException("Reject reason is invalid");
        }
        return rejectionReasonObject.get();
    }
}
