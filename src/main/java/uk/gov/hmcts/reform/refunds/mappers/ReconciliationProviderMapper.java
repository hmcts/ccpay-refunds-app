package uk.gov.hmcts.reform.refunds.mappers;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.dtos.PaymentFeeDetailsRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ReconciliationProviderRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ReconcilitationProviderFeeRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentFeeResponse;
import uk.gov.hmcts.reform.refunds.model.Refund;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ReconciliationProviderMapper {

    public ReconciliationProviderRequest getReconciliationProviderRequest(PaymentFeeDetailsRequest paymentDto, Refund refund){
        return ReconciliationProviderRequest.refundReconciliationProviderRequestWith()
            .refundReference(refund.getReference())
            .paymentReference(paymentDto.getPaymentReference())
            .dateCreated(refund.getDateCreated())
            .dateUpdated(refund.getDateUpdated())
            .refundReason(refund.getReason())
            .totalRefundAmount(refund.getAmount())
            .currency("GBP")
            .caseReference(paymentDto.getCaseReference())
            .ccdCaseNumber(paymentDto.getCcdCaseNumber())
            .accountNumber(paymentDto.getAccountNumber())
            .fees(getRefundRequestFees(paymentDto.getFees()))
            .build();
    }


    private List<ReconcilitationProviderFeeRequest> getRefundRequestFees(List<PaymentFeeResponse> fees) {
        return fees.stream().map(feeDto -> feeDtoMapToReconcilitationProviderFeeRequest(feeDto))
            .collect(Collectors.toList());
    }

    private ReconcilitationProviderFeeRequest feeDtoMapToReconcilitationProviderFeeRequest(PaymentFeeResponse paymentFeeResponse){
        return ReconcilitationProviderFeeRequest.refundReconcilitationProviderFeeRequest()
            .code(paymentFeeResponse.getCode())
            .refundAmount(paymentFeeResponse.getFeeAmount())
            .version(paymentFeeResponse.getVersion())
            .build();
    }
}
