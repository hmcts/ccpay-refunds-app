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

@SuppressWarnings({"PMD.TooManyMethods"})
@Repository
public interface RefundsRepository extends CrudRepository<Refund, Integer>, JpaSpecificationExecutor<Refund> {
    Optional<List<Refund>> findByPaymentReference(String paymentReference);

    Optional<List<Refund>> findByPaymentReferenceInAndRefundStatusNotIn(List<String> paymentReference, List<RefundStatus> refundStatus);

    Optional<Refund> findByReference(String reference);

    default Refund findByReferenceOrThrow(String reference) {
        return findByReference(reference).orElseThrow(() -> new RefundNotFoundException(
            "Refund not found for given reference"));
    }

    Optional<List<Refund>> findByRefundStatusAndUpdatedByIsNotAndServiceTypeInIgnoreCase(RefundStatus refundStatus,
                                                                        String updatedBy, List<String> serviceName);

    Optional<List<Refund>> findByCcdCaseNumberAndServiceTypeInIgnoreCase(String ccdCaseNumber, List<String> serviceName);

    Optional<List<Refund>> findByRefundStatus(RefundStatus refundStatus);

    Optional<List<Refund>> findByRefundStatusAndServiceTypeInIgnoreCase(RefundStatus refundStatus, List<String> serviceName);

    Optional<List<Refund>> findByCcdCaseNumber(String ccdCaseNumber);

    long deleteByReference(String reference);

    Optional<List<Refund>> findByNotificationSentFlag(String notificationSentFlag);

    @Query("select rf from Refund rf "
        + "where rf.dateUpdated NOT between ?1 and ?2 AND (rf.refundStatus = 'Approved' or rf.refundStatus = 'Accepted')")
    List<Refund> findByDatesBetween(Date fromDate, Date toDate);

    @Query("select rf from Refund rf "
        + "where rf.paymentReference = ?1  AND (rf.refundStatus = 'Approved' or rf.refundStatus = 'Accepted')"
        + "AND rf.reference NOT IN(?2)")
    List<Refund> findAllByPaymentReference(String paymentReference,String reference);

}
