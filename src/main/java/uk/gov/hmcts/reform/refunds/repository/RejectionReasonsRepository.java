package uk.gov.hmcts.reform.refunds.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.refunds.model.RejectionReason;

import java.util.Optional;

@Repository
public interface RejectionReasonsRepository extends CrudRepository<RejectionReason, String> {
    Optional<RejectionReason> findByCode(String reasonCode);
}
