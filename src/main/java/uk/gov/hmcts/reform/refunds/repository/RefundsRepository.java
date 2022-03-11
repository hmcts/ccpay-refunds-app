package uk.gov.hmcts.reform.refunds.repository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.refunds.exceptions.RefundNotFoundException;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefundsRepository extends CrudRepository<Refund, Integer>, JpaSpecificationExecutor<Refund> {
    Optional<List<Refund>> findByPaymentReference(String paymentReference);

    Optional<Refund> findByReference(String reference);

    default Refund findByReferenceOrThrow(String reference) {
        return findByReference(reference).orElseThrow(() -> new RefundNotFoundException(
            "Refund not found for given reference"));
    }

    Optional<List<Refund>> findByRefundStatusAndCreatedByIsNot(RefundStatus refundStatus, String createdBy);

    Optional<List<Refund>> findByRefundStatusAndUpdatedByIsNot(RefundStatus refundStatus, String updatedBy);

    Optional<List<Refund>> findByRefundStatus(RefundStatus refundStatus);

    Optional<List<Refund>> findByCcdCaseNumber(String ccdCaseNumber);

    Optional<List<Refund>> findByCcdCaseNumberAndCreatedBy(String ccdCaseNumber, String createdBy);

    Optional<List<Refund>> findByNotificationSentFlag(String notificationSentFlag);

    Optional<List<Refund>> findByRefundStatusAndRefundApproveFlag(String refundsStatus, String liberataSentFlag);

}
