package uk.gov.hmcts.reform.refunds.mappers;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.dtos.requests.ReconciliationProviderRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ReconcilitationProviderFeeRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentFeeResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentGroupResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RemissionResponse;
import uk.gov.hmcts.reform.refunds.exceptions.FeesNotFoundForRefundException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundFeeNotFoundInPaymentException;
import uk.gov.hmcts.reform.refunds.exceptions.RetrospectiveRemissionNotFoundException;
import uk.gov.hmcts.reform.refunds.exceptions.UnequalRemissionAmountWithRefundRaisedException;
import uk.gov.hmcts.reform.refunds.model.Refund;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ReconciliationProviderMapper {

    public ReconciliationProviderRequest getReconciliationProviderRequest(PaymentGroupResponse paymentDto, Refund refund){
        return ReconciliationProviderRequest.refundReconciliationProviderRequestWith()
            .refundReference(refund.getReference())
            .paymentReference(paymentDto.getPayments().get(0).getReference())
            .dateCreated(refund.getDateCreated())
            .dateUpdated(refund.getDateUpdated())
            .refundReason(refund.getReason())
            .totalRefundAmount(refund.getAmount())
            .currency("GBP")
            .caseReference(paymentDto.getPayments().get(0).getCaseReference())
            .ccdCaseNumber(paymentDto.getPayments().get(0).getCcdCaseNumber())
            .accountNumber(paymentDto.getPayments().get(0).getAccountNumber())
            .fees(getRefundRequestFees(refund, paymentDto))
            .build();
    }


    private List<ReconcilitationProviderFeeRequest> getRefundRequestFees(Refund refund, PaymentGroupResponse paymentGroupResponse) {
        String feeIds = refund.getFeeIds();
        List<Integer> refundFeeIds = getRefundFeeIds(feeIds);
        if(!refundFeeIds.isEmpty()){
            List<RemissionResponse> remissionsAppliedForRefund = paymentGroupResponse.getRemissions().stream().filter(remissionResponse -> refundFeeIds.contains(remissionResponse.getFeeId())).collect(
                Collectors.toList());
            if( refund.getReason().equals("RR036-Retrospective remission")){
                // create a constant and add the code
                validateRetrospectiveRemissions(remissionsAppliedForRefund,refund);
                List<PaymentFeeResponse> feeResponses = getRetrospectiveRemissionAppliedFee(paymentGroupResponse, refundFeeIds);
                return Arrays.asList(ReconcilitationProviderFeeRequest.refundReconcilitationProviderFeeRequest()
                                         .version(feeResponses.get(0).getVersion())
                                         .code(feeResponses.get(0).getCode())
                                         .refundAmount(remissionsAppliedForRefund.get(0).getHwfAmount())
                                         .build());
            }

            return paymentGroupResponse.getFees().stream().map(paymentFeeResponse -> {
                List<RemissionResponse> remissionForGivenFee = remissionsAppliedForRefund.stream().filter(remissionResponse ->
                                                                                                            remissionResponse.getFeeId().equals(paymentFeeResponse.getId())).collect(
                    Collectors.toList());
                BigDecimal refundAmount = remissionForGivenFee.isEmpty()?paymentFeeResponse.getApportionAmount():paymentFeeResponse.getApportionAmount().subtract(remissionForGivenFee.get(0).getHwfAmount());
                return ReconcilitationProviderFeeRequest.refundReconcilitationProviderFeeRequest()
                    .code(paymentFeeResponse.getCode())
                    .version(paymentFeeResponse.getVersion())
                    .refundAmount(refundAmount)
                    .build();
            }).collect(Collectors.toList());
        }
        throw new FeesNotFoundForRefundException("Fee not found in Refund");
    }

    private List<Integer> getRefundFeeIds(String refundIds) {
        return  Arrays.stream(refundIds.split(",")).map(feeId->Integer.parseInt(feeId)).collect(Collectors.toList());
    }

    private void validateRetrospectiveRemissions(List<RemissionResponse> remissionsAppliedForRefund, Refund refund ){
        if(remissionsAppliedForRefund.isEmpty()){
            throw new RetrospectiveRemissionNotFoundException("Remission not found");
        }
        if(!remissionsAppliedForRefund.isEmpty() && !remissionsAppliedForRefund.get(0).getHwfAmount().equals(refund.getAmount())){
            throw new UnequalRemissionAmountWithRefundRaisedException("Remission amount not equal to refund amount");
        }
    }

    private List<PaymentFeeResponse> getRetrospectiveRemissionAppliedFee(PaymentGroupResponse paymentGroupResponse, List<Integer> refundFeeIds ){
        List<PaymentFeeResponse> feeResponses = paymentGroupResponse.getFees().stream().filter(feeResponse -> feeResponse.getId().equals(refundFeeIds.get(0))).collect(
            Collectors.toList());
        if(feeResponses.isEmpty()){
            throw new RefundFeeNotFoundInPaymentException("Refund not found in payment");
        }
        return feeResponses;
    }
}
