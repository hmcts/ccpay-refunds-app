package uk.gov.hmcts.reform.refunds.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

@Service
public class RefundsDomainServiceImpl implements RefundsDomainService {

    @Autowired
    RefundsRepository refundsRepository;

    @Override
    public Refund saveRefund() {
        Refund refund = Refund.refundsWith()
                            .refundsId(UUID.randomUUID().toString())
                            .dateCreated(Timestamp.from(Instant.now()))
                            .dateUpdated(Timestamp.from(Instant.now()))
                            .build();
        return refundsRepository.save(refund);
    }
}
