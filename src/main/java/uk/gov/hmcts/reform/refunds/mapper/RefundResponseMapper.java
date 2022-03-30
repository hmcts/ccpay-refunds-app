package uk.gov.hmcts.reform.refunds.mapper;

import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundFeeDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.RefundDto;
import uk.gov.hmcts.reform.refunds.dtos.responses.UserIdentityDataDto;
import uk.gov.hmcts.reform.refunds.model.Refund;

import java.util.ArrayList;
import java.util.List;
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
            .build();
    }

}
