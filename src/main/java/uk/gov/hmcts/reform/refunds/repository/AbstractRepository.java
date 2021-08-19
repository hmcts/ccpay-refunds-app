package uk.gov.hmcts.reform.refunds.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundRequestException;

import java.io.Serializable;
import java.util.Optional;

@NoRepositoryBean
@SuppressWarnings("PMD.GenericsNaming")
public interface AbstractRepository<T, ID extends Serializable> extends JpaRepository<T, ID> {

    Optional<T> findByCode(String code);

    default T findByCodeOrThrow(String code) {
        return findByCode(code).orElseThrow(() -> new InvalidRefundRequestException("Invalid Reason Type : " + code));
    }

}
