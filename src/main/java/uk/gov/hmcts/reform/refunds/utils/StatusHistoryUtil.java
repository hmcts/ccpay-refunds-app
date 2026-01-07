package uk.gov.hmcts.reform.refunds.utils;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.repository.StatusHistoryRepository;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static uk.gov.hmcts.reform.refunds.model.RefundStatus.REISSUED;

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

    // Builds the label for the status history notes when re-issuing a refund.
    public String getReissueLabel(Refund refund) {
        // Determine the original refund reference for this chain
        String originalRefundReference = getOriginalRefundReference(refund);
        if (StringUtils.isBlank(originalRefundReference)) {
            originalRefundReference = refund.getReference();
        }

        // Look at all refunds for this payment reference and count how many times
        // we've already re-issued for the same original reference
        List<Refund> relatedRefunds = refundsRepository
            .findByPaymentReference(refund.getPaymentReference())
            .orElse(Collections.emptyList());

        final String marker = "original refund " + originalRefundReference;

        // Count distinct refund references that already recorded a REISSUED event for this original reference
        int expiredCount = relatedRefunds.stream()
            .filter(r -> r.getStatusHistories() != null)
            .filter(r -> r.getStatusHistories().stream().anyMatch(h ->
                                                                      REISSUED.getName().equals(h.getStatus())
                                                                          && StringUtils.containsIgnoreCase(h.getNotes(), marker)))
            .map(Refund::getReference)
            .collect(Collectors.toSet())
            .size() + 1; // next re-issue number

        return toOrdinal(expiredCount) + " re-issue of original refund " + originalRefundReference;
    }

    // Converts 1 -> "1st", 2 -> "2nd", 3 -> "3rd", 4 -> "4th", etc.
    private static String toOrdinal(int n) {
        int mod100 = n % 100;
        if (mod100 >= 11 && mod100 <= 13) {
            return n + "th";
        }
        switch (n % 10) {
            case 1:  return n + "st";
            case 2:  return n + "nd";
            case 3:  return n + "rd";
            default: return n + "th";
        }
    }

}
