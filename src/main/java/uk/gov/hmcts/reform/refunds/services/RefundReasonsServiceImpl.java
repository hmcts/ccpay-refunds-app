package uk.gov.hmcts.reform.refunds.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.refunds.model.RefundReason;
import uk.gov.hmcts.reform.refunds.repository.RefundReasonRepository;

import java.util.List;

@Service
public class RefundReasonsServiceImpl implements RefundReasonsService {

    @Autowired
    RefundReasonRepository refundsReasonRepository;

    @Override
    public List<RefundReason> findAll() {
        return refundsReasonRepository.findAll();
    }
}
