package uk.gov.hmcts.reform.refunds.repository;

import jakarta.persistence.Tuple;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.refunds.exceptions.RefundNotFoundException;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;

import java.util.Date;
import java.util.List;
import java.util.Optional;

@SuppressWarnings({"PMD.TooManyMethods"})
@Repository
public interface RefundsRepository extends ListCrudRepository<Refund, Integer>, JpaSpecificationExecutor<Refund> {
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
        + "where rf.dateUpdated NOT between ?1 and ?2 AND (rf.refundStatus.name = 'Approved' or rf.refundStatus.name = 'Accepted')")
    List<Refund> findByDatesBetween(Date fromDate, Date toDate);

    @Query("select rf from Refund rf "
        + "where rf.paymentReference = ?1  AND (rf.refundStatus.name = 'Approved' or rf.refundStatus.name = 'Accepted')"
        + "AND rf.reference NOT IN(?2)")
    List<Refund> findAllByPaymentReference(String paymentReference,String reference);

    @Query(value = "SELECT r.date_created,r.date_updated,r.amount,"
        + "r.reference,r.payment_reference,r.ccd_case_number,"
        + "r.service_type,r.refund_status,sh.notes "
        + "FROM refunds r "
        + "INNER JOIN status_history sh ON r.id = sh.refunds_id AND r.refund_status = sh.status "
        + "WHERE (r.refund_status NOT IN ('Sent for approval', 'Update required') "
        + "OR (r.refund_status = 'Rejected' AND sh.notes != 'Rejected by Team Leader') ) "
        + "AND r.date_created BETWEEN :fromDate AND :toDate", nativeQuery = true)
    List<Tuple> findAllRefundsByDateCreatedBetween(
        @Param("fromDate") Date fromDate,
        @Param("toDate") Date toDate);
}
