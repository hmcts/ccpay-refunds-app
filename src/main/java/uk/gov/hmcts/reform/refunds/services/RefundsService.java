package uk.gov.hmcts.reform.refunds.services;

import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.springframework.util.MultiValueMap;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResubmitRefundRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundLiberata;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundListDtoResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RejectionReasonResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.ResubmitRefundResponseDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.StatusHistoryResponseDto;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.state.RefundEvent;

import java.util.List;
import java.util.Optional;

public interface RefundsService {

    RefundResponse initiateRefund(RefundRequest refundRequest, MultiValueMap<String, String> headers) throws CheckDigitException;

    RefundEvent[] retrieveActions(String reference);

    Refund getRefundForReference(String reference);

    RefundListDtoResponse getRefundList(String status, MultiValueMap<String, String> headers, String ccdCaseNumber,
                                        String excludeCurrentUser);

    List<RejectionReasonResponse> getRejectedReasons();

    StatusHistoryResponseDto getStatusHistory(MultiValueMap<String, String> headers, String reference);

    ResubmitRefundResponseDto resubmitRefund(String reference, ResubmitRefundRequest request, MultiValueMap<String, String> headers);

    void deleteRefund(String reference);

    List<Refund> getRefundsForPaymentReference(String paymentReference);

    List<RefundLiberata> search(Optional<String> startDateTimeString, Optional<String>  endDateTimeString, String refundReference);
}
