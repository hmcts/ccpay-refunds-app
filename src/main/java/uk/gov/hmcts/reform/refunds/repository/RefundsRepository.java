package uk.gov.hmcts.reform.refunds.repository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.refunds.exceptions.RefundNotFoundException;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;

import java.util.Date;
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

    Optional<List<Refund>> findByRefundStatusAndUpdatedByIsNot(RefundStatus refundStatus, String updatedBy);

    Optional<List<Refund>> findByRefundStatus(RefundStatus refundStatus);

    Optional<List<Refund>> findByCcdCaseNumber(String ccdCaseNumber);

    long deleteByReference(String reference);

    Optional<List<Refund>> findByNotificationSentFlag(String notificationSentFlag);

    Optional<List<Refund>> findByRefundStatusAndRefundApproveFlag(String refundsStatus, String liberataSentFlag);

    @Query("select pf from Refund pf "
        + "where pf.dateUpdated NOT between ?1 and ?2 AND (pf.refundStatus = 'Approved' or pf.refundStatus = 'Accepted')")
    List<Refund> findByDatesBetween(Date fromDate, Date toDate);

}
