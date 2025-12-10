package uk.gov.hmcts.reform.refunds.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.repository.StatusHistoryRepository;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class StatusHistoryUtil {

    @Autowired
    private StatusHistoryRepository statusHistoryRepository;

    @Autowired
    private RefundsRepository refundsRepository;

    private static final Pattern PAYMENT_REF_PATTERN = Pattern.compile("\\bRF-\\d{4}-\\d{4}-\\d{4}-\\d{4}\\b");

    public boolean isAClonedRefund(Refund refund) {
        return statusHistoryRepository.findByRefundOrderByDateCreatedDesc(refund)
            .stream()
            .anyMatch(history -> RefundStatus.REISSUED.getName().equals(history.getStatus()));
    }

    public String getOriginalRefundReference(Refund refund) {
        if (isAClonedRefund(refund)) {
            // For cloned refunds, get the reference from the first REISSUED status history
            List<StatusHistory> statusHistories = statusHistoryRepository.findByRefundOrderByDateCreatedDesc(refund);
            Optional<StatusHistory> firstReissued = statusHistories
                .stream()
                .filter(history -> RefundStatus.REISSUED.getName().equals(history.getStatus()))
                .findFirst();
            if (firstReissued.isPresent()) {
                return extractRefundReference(firstReissued.get().getNotes());
            } else {
                return null;
            }
        }
        // For non-cloned refunds, return the current refund reference
        return refund.getReference();
    }

    public String extractRefundReference(String input) {
        Matcher matcher = PAYMENT_REF_PATTERN.matcher(input);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    public String getOriginalNoteForRejected(Refund refund) {
        Optional<StatusHistory> statusHistories = statusHistoryRepository.findByRefundOrderByDateCreatedDesc(refund).stream()
            .filter(history -> RefundStatus.REJECTED.getName().equals(history.getStatus()))
            .findFirst();

        if (statusHistories.isPresent()) {
            return statusHistories.get().getNotes();
        } else {
            return null;
        }
    }

}
