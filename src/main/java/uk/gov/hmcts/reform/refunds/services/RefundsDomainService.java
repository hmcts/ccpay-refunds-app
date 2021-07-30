package uk.gov.hmcts.reform.refunds.services;

import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundResponse;
import uk.gov.hmcts.reform.refunds.model.Refund;

import java.util.Map;


public interface RefundsDomainService {

    Refund saveRefund();

    RefundResponse getRefundReference(MultiValueMap<String,String> headers, RefundRequest refundRequest) throws CheckDigitException;

    Refund retrieve(String reference);
}
