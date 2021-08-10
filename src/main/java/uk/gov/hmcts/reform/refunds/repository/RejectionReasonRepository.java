package uk.gov.hmcts.reform.refunds.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RejectionReason;

import java.util.List;
import java.util.Optional;

@Repository
public interface RejectionReasonRepository extends CrudRepository<RejectionReason, Integer> {
    @Override
    List<RejectionReason> findAll();

}
