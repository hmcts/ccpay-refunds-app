package uk.gov.hmcts.reform.refunds.services;

import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.springframework.http.HttpStatus;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundResponse;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;

import java.util.List;

public interface RefundsService {

    RefundResponse initiateRefund(RefundRequest refundRequest, MultiValueMap<String, String> headers) throws CheckDigitException;

    RefundEvent[] retrieveActions(String reference);

    Refund getRefundForReference(String reference);

//    HttpStatus reSubmitRefund(MultiValueMap<String, String> headers, String refundReference, RefundRequest refundRequest);

    List<String> getRejectedReasons();
}
