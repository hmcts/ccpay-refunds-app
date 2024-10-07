package uk.gov.hmcts.reform.refunds.repository;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.stereotype.Repository;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;

import java.util.List;

@Repository
public interface StatusHistoryRepository extends ListCrudRepository<StatusHistory, Long> {

    List<StatusHistory> findByRefundOrderByDateCreatedDesc(Refund refund);

}

