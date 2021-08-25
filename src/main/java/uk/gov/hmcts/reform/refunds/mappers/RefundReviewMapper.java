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

    public String getStatus(RefundEvent refundEvent) {
        if (refundEvent.equals(RefundEvent.APPROVE)) {
            return "approved";
        }
        return refundEvent.equals(RefundEvent.REJECT) ? "rejected" : "sentback";
    }

    public String getStatusNotes(RefundEvent refundEvent, RefundReviewRequest refundReviewRequest) {
        String notes;
        if (refundEvent.equals(RefundEvent.APPROVE)) {
            notes = "Refund Approved";
        } else if (refundEvent.equals(RefundEvent.REJECT) || refundEvent.equals(RefundEvent.SENDBACK)) {

            if (refundEvent.REJECT.equals(refundEvent)) {
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
        if (null != refundReviewRequest && refundReviewRequest.getCode() == null || refundReviewRequest.getCode().isEmpty()) {
            throw new InvalidRefundReviewRequestException("Refund reject reason is required");
        }
        if ("RE005".equals(refundReviewRequest.getCode())) {
            if (refundReviewRequest.getReason() == null || refundReviewRequest.getReason().isEmpty()) {
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
