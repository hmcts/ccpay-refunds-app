package uk.gov.hmcts.reform.refunds.services;

import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.refunds.model.Refund;


public interface RefundsService {

    Refund saveRefund();
}
