package uk.gov.hmcts.reform.refunds.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.refunds.model.Refund;

@Repository
public interface RefundsRepository extends CrudRepository<Refund, Integer> {
}
