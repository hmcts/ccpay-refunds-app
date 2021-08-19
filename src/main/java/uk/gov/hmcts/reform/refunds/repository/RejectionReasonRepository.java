package uk.gov.hmcts.reform.refunds.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.refunds.model.RejectionReasons;

import java.util.List;

@Repository
public interface RejectionReasonRepository extends CrudRepository<RejectionReasons, Integer> {
    @Override
    List<RejectionReasons> findAll();

}
