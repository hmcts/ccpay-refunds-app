package uk.gov.hmcts.reform.refunds.mapper;

import org.apache.commons.collections.CollectionUtils;
import org.eclipse.collections.impl.collector.Collectors2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundFeeDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.FeeDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentFeeLibarataResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentRefundDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundLiberata;
import uk.gov.hmcts.reform.refunds.dtos.responses.UserIdentityDataDto;

import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundFees;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RefundResponseMapper {

    @Autowired
    private RefundFeeMapper refundFeeMapper;

    // To enable unit testing
    public void setRefundFeeMapper(RefundFeeMapper refundFeeMapper) {
        this.refundFeeMapper = refundFeeMapper;
    }

    public RefundDto getRefundListDto(Refund refund, UserIdentityDataDto userData,String reason) {
        List<RefundFeeDto> refundFeesDtoList = new ArrayList<>();
        if (CollectionUtils.isNotEmpty(refund.getRefundFees())) {
            refundFeesDtoList.addAll(refund.getRefundFees().stream()
                                         .map(refundFeeMapper::toRefundFeeDto)
                                         .collect(Collectors.toList()));
        }

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
            .refundFees(refundFeesDtoList)
            .reasonCode(refund.getReason())
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

    @SuppressWarnings({"PMD"})
    private List<PaymentFeeLibarataResponse> toFeeDtos(List<FeeDto> paymentFees,Refund refund) {

        List<List<FeeDto>> groupedFee = paymentFees.stream()
            .collect(Collectors.groupingBy(o -> Pair.of(o.getCode(), o.getNaturalAccountCode())))
            .entrySet().stream()
            .map(Map.Entry::getValue).collect(Collectors.toList());
        List<FeeDto> feeDtoList = new ArrayList<>();
        Iterator<List<FeeDto>> groupedFeeIterator = groupedFee.iterator();
        while (groupedFeeIterator.hasNext()) {
            List<FeeDto> feeDtoL = groupedFeeIterator.next();
            FeeDto feeDto = new FeeDto();
            feeDto.setCode(feeDtoL.get(0).getCode());
            feeDto.setJurisdiction1(feeDtoL.get(0).getJurisdiction1());
            feeDto.setJurisdiction2(feeDtoL.get(0).getJurisdiction2());
            feeDto.setMemoLine(feeDtoL.get(0).getMemoLine());
            feeDto.setNaturalAccountCode(feeDtoL.get(0).getNaturalAccountCode());
            feeDto.setVersion(feeDtoL.get(0).getVersion());
            feeDtoList.add(feeDto);
        }

        return feeDtoList.stream().filter(pf -> refund.getRefundFees().stream().anyMatch(id -> id.getCode().equals(pf.getCode())))
            .map(pf -> toFeeDto(pf,refund)).collect(Collectors.toList());
    }

    private PaymentFeeLibarataResponse toFeeDto(FeeDto fee, Refund refund) {
        return PaymentFeeLibarataResponse.feeLibarataDtoWith()
            .credit(toCreditAmount(refund,fee))
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

        for (Map.Entry<String, BigDecimal> entry : groupByPaymentReference.entrySet()) {

            if (entry.getKey().equals(paymentDto.getPaymentReference())) {
                avlAmount =  paymentDto.getAmount().subtract(entry.getValue());
            }

        }
        return avlAmount;
    }


    private BigDecimal toCreditAmount(Refund refund, FeeDto fee) {

        Map<String, BigDecimal> groupByFeeCode =
            refund.getRefundFees().stream().collect(Collectors.groupingBy(RefundFees::getCode,
                                                                          Collectors2.summingBigDecimal(RefundFees::getRefundAmount)));

        BigDecimal creditAmount = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal> entry : groupByFeeCode.entrySet()) {
            if (fee.getCode().equals(entry.getKey())) {
                creditAmount = entry.getValue();
            }
        }
        return creditAmount;
    }

}
