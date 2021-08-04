package uk.gov.hmcts.reform.refunds.services;

import uk.gov.hmcts.reform.refunds.model.RefundReason;

import java.util.List;

public interface RefundReasonsService {
    List<RefundReason> findAll();
}
