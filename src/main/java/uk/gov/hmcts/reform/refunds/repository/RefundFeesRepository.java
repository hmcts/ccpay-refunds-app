package uk.gov.hmcts.reform.refunds.repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.refunds.model.RefundFees;

import java.util.List;

@Repository
public interface RefundFeesRepository extends CrudRepository<RefundFees, Integer> {

    @Modifying
    @Transactional
    @Query("delete from RefundFees where id in(:refundFeeId)")
    void deleteByIdIn(List<Integer> refundFeeId);
}
