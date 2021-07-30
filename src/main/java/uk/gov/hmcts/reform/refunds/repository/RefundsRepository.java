package uk.gov.hmcts.reform.refunds.repository;

import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.refunds.model.Refund;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefundsRepository extends CrudRepository<Refund, Integer>, JpaSpecificationExecutor<Refund> {
    Optional<List<Refund>> findByPaymentReference(String paymentReference);
    Refund findByReference(String reference);
}
