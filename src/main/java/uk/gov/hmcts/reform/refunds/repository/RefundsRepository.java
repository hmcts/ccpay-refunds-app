package uk.gov.hmcts.reform.refunds.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.refunds.model.Refund;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefundsRepository extends CrudRepository<Refund, Integer> {
    Optional<List<Refund>> findByPaymentReference(String paymentReference);

    Optional<Refund> findByReference(String reference);
}
