package uk.gov.hmcts.reform.refunds.repository;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundRequestException;
import uk.gov.hmcts.reform.refunds.model.RefundReason;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional(readOnly = true)
public interface RefundReasonRepository extends ListCrudRepository<RefundReason, String> {

    @Override
    List<RefundReason> findAll();

    Optional<RefundReason> findByCode(String code);

    default RefundReason findByCodeOrThrow(String code) {
        return findByCode(code).orElseThrow(() -> new InvalidRefundRequestException("Invalid Reason Type : " + code));
    }

}


