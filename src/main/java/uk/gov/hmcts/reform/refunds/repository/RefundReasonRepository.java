package uk.gov.hmcts.reform.refunds.repository;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.refunds.model.RefundReason;

import java.util.List;

@Repository
@Transactional(readOnly = true)
public interface RefundReasonRepository extends CrudRepository<RefundReason, String> {

    @Override
    List<RefundReason> findAll();
}


