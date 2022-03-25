package uk.gov.hmcts.reform.refunds.mapper;

import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.dtos.responses.FeeDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentFeeLibarataResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentRefundDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundLiberata;
import uk.gov.hmcts.reform.refunds.dtos.responses.UserIdentityDataDto;
import uk.gov.hmcts.reform.refunds.model.Refund;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RefundResponseMapper {


    public RefundDto getRefundListDto(Refund refund, UserIdentityDataDto userData, String reason) {
        return RefundDto
            .buildRefundListDtoWith()
            .ccdCaseNumber(refund.getCcdCaseNumber())
            .amount(refund.getAmount())
            .reason(reason)
            .refundStatus(refund.getRefundStatus())
            .refundReference(refund.getReference())
            .paymentReference(refund.getPaymentReference())
            .userFullName(userData == null ? "" : userData.getFullName())
            .emailId(userData == null ? "" : userData.getEmailId())
            .dateCreated(refund.getDateCreated().toString())
            .dateUpdated(refund.getDateUpdated().toString())
            .contactDetails(refund.getContactDetails())
            .serviceType(refund.getServiceType())
            .feeIds(refund.getFeeIds())
            .refundFees(refund.getRefundFees())
            .build();

    }

    public RefundLiberata getRefundLibrata(Refund refund, PaymentDto paymentDto, Map<String, BigDecimal> groupByPaymentReference) {

        return RefundLiberata.buildRefundLibarataWith()
                        .dateApproved(refund.getDateUpdated())
                        .totalRefundAmount(refund.getAmount())
                        .instructionType(refund.getRefundInstructionType())
                        .reason(refund.getReason())
                        .reference(refund.getReference())
                        .payment(toPayment(paymentDto, groupByPaymentReference))
                        .fees(toFeeDtos(paymentDto.getFees(), refund))
                        .build();

    }

    private  PaymentRefundDto toPayment(PaymentDto payment, Map<String, BigDecimal> groupByPaymentReference) {

        return   PaymentRefundDto.paymentRefundDtoWith()
            .pbaNumber(payment.getAccountNumber())
            .ccdCaseNumber(payment.getCcdCaseNumber())
            .bgcNumber(payment.getGiroSlipNo())
            .reference(payment.getPaymentReference())
            .caseReference(payment.getCaseReference())
            .channel(payment.getChannel())
            .customerReference(payment.getCustomerReference())
            .dateReceiptCreated(payment.getDateCreated())
            .govUkId(payment.getExternalReference())
            .method(payment.getMethod())
            .serviceName(payment.getServiceName())
            .siteId(payment.getSiteId())
            .availableFunds(availableFunds(groupByPaymentReference,payment))
            .build();


    }

    private List<PaymentFeeLibarataResponse> toFeeDtos(List<FeeDto> paymentFees,Refund refund) {
        return paymentFees.stream().map(f -> toFeeDto(f,refund)).collect(Collectors.toList());
    }

    private PaymentFeeLibarataResponse toFeeDto(FeeDto fee, Refund refund) {
        return PaymentFeeLibarataResponse.feeLibarataDtoWith()
            .id(fee.getId())
            .credit(refund.getAmount())
            .code(fee.getCode())
            .jurisdiction1(fee.getJurisdiction1())
            .jurisdiction2(fee.getJurisdiction2())
            .naturalAccountCode(fee.getNaturalAccountCode())
            .memoLine(fee.getMemoLine())
            .version(fee.getVersion())
            .build();
    }

    private BigDecimal availableFunds(Map<String, BigDecimal> groupByPaymentReference, PaymentDto paymentDto) {

        BigDecimal avlAmount = BigDecimal.ZERO;
        Set<Map.Entry<String, BigDecimal>> entrySet = groupByPaymentReference.entrySet();

        for (Map.Entry<String, BigDecimal> entry : entrySet) {

            if (entry.getKey().equals(paymentDto.getPaymentReference())) {

                avlAmount =  paymentDto.getAmount().subtract(entry.getValue());

            }
        }

        return avlAmount;

    }

}
