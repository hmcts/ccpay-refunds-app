package uk.gov.hmcts.reform.refunds.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.refunds.model.RefundReason;

import java.util.List;

@Repository
public interface RefundReasonsRepository extends CrudRepository<RefundReason, String> {
    @Override
    List<RefundReason> findAll();
}
