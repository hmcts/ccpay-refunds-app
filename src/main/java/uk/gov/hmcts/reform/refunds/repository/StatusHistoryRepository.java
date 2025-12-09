package uk.gov.hmcts.reform.refunds.repository;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;

import java.util.List;
import java.util.Optional;

@Repository
public interface StatusHistoryRepository extends ListCrudRepository<StatusHistory, Long> {

    List<StatusHistory> findByRefundOrderByDateCreatedDesc(Refund refund);

    default boolean isAClonedRefund(Refund refund) {
        return findByRefundOrderByDateCreatedDesc(refund)
            .stream()
            .anyMatch(history -> RefundStatus.REISSUED.getName().equals(history.getStatus()));
    }

    default Refund getOriginalRefund(Refund refund) {
        if (isAClonedRefund(refund)) {
            // For cloned refunds, get the reference from the first REISSUED status history
            List<StatusHistory> statusHistories = findByRefundOrderByDateCreatedDesc(refund);
            Optional<StatusHistory> firstReissued = statusHistories
                .stream()
                .filter(history -> RefundStatus.REISSUED.getName().equals(history.getStatus()))
                .findFirst();
            return firstReissued.map(StatusHistory::getRefund).orElse(null);
        }
        // For non-cloned refunds, return the current refund
        return refund;
    }

    default String getOriginalNoteForRejected(Refund refund) {
        Optional<StatusHistory> statusHistories = findByRefundOrderByDateCreatedDesc(refund).stream()
            .filter(history -> RefundStatus.REJECTED.getName().equals(history.getStatus()))
            .findFirst();

        if (statusHistories.isPresent()) {
            return statusHistories.get().getNotes();
        } else {
            return null;
        }
    }
}
