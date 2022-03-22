package uk.gov.hmcts.reform.refunds.mappers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.dtos.requests.ReconciliationProviderRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ReconcilitationProviderFeeRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentFeeResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentGroupResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RemissionResponse;
import uk.gov.hmcts.reform.refunds.exceptions.FeesNotFoundForRefundException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundFeeNotFoundInPaymentException;
import uk.gov.hmcts.reform.refunds.exceptions.RefundReasonNotFoundException;
import uk.gov.hmcts.reform.refunds.exceptions.RetrospectiveRemissionNotFoundException;
import uk.gov.hmcts.reform.refunds.exceptions.UnequalRemissionAmountWithRefundRaisedException;
import uk.gov.hmcts.reform.refunds.mapper.RefundFeeMapper;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundReason;
import uk.gov.hmcts.reform.refunds.repository.RefundReasonRepository;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ReconciliationProviderMapper {
    private static final Logger LOG = LoggerFactory.getLogger(ReconciliationProviderMapper.class);
    @Autowired
    private RefundReasonRepository refundReasonRepository;

    @Autowired
    private RefundFeeMapper refundFeeMapper;

    public ReconciliationProviderRequest getReconciliationProviderRequest(PaymentGroupResponse paymentDto, Refund refund) {
        LOG.info("paymentDto != null: {}", paymentDto != null);
        logPaymentDto(paymentDto);
        return ReconciliationProviderRequest.refundReconciliationProviderRequestWith()
            .refundReference(refund.getReference())
            .paymentReference(paymentDto.getPayments().get(0).getReference())
            .dateCreated(getDate(refund.getDateCreated()))
            .dateUpdated(getDate(refund.getDateUpdated()))
            .refundReason(getRefundReason(refund.getReason()))
            .totalRefundAmount(refund.getAmount().toString())
            .currency("GBP")
            .caseReference(paymentDto.getPayments().get(0).getCaseReference())
            .ccdCaseNumber(paymentDto.getPayments().get(0).getCcdCaseNumber())
            .accountNumber(paymentDto.getPayments().get(0).getAccountNumber())
            .fees(getRefundRequestFees(refund, paymentDto))
            .build();
    }

    private void logPaymentDto(PaymentGroupResponse paymentDto) {
        if (paymentDto != null) {
            LOG.info("paymentDto.getPayments(): {}", paymentDto.getPayments());
            if (paymentDto.getPayments() != null
                && !paymentDto.getPayments().isEmpty()) {
                LOG.info("paymentDto.getPayments().get(0): {}", paymentDto.getPayments().get(0));
            }
        }
    }

    private List<ReconcilitationProviderFeeRequest> getRefundRequestFees(Refund refund, PaymentGroupResponse paymentGroupResponse) {
        String feeIds = refund.getFeeIds();
        List<Integer> refundFeeIds = getRefundFeeIds(feeIds);
        if (!refundFeeIds.isEmpty()) {
            List<RemissionResponse> remissionsAppliedForRefund = paymentGroupResponse.getRemissions().stream()
                .filter(remissionResponse -> refundFeeIds.contains(remissionResponse.getFeeId())).collect(
                Collectors.toList());
            if (refund.getReason().equals("RR036")) {
                // create a constant and add the code
                validateRetrospectiveRemissions(remissionsAppliedForRefund,refund);
                List<PaymentFeeResponse> feeResponses = getRetrospectiveRemissionAppliedFee(paymentGroupResponse, refundFeeIds);
                return Arrays.asList(ReconcilitationProviderFeeRequest.refundReconcilitationProviderFeeRequest()
                                         .version(Integer.parseInt(feeResponses.get(0).getVersion()))
                                         .code(feeResponses.get(0).getCode())
                                         .refundAmount(remissionsAppliedForRefund.get(0).getHwfAmount().toString())
                                         .build());
            }

            return refund.getRefundFees().stream().map(refundFeeMapper::toRefundFeeForReconcilitationProvider
            ).collect(Collectors.toList());
        }
        throw new FeesNotFoundForRefundException("Fee not found in Refund");
    }

    private List<Integer> getRefundFeeIds(String refundIds) {
        return  Arrays.stream(refundIds.split(",")).map(feeId -> Integer.parseInt(feeId)).collect(Collectors.toList());
    }

    private void validateRetrospectiveRemissions(List<RemissionResponse> remissionsAppliedForRefund, Refund refund) {
        if (remissionsAppliedForRefund.isEmpty()) {
            throw new RetrospectiveRemissionNotFoundException("Remission not found");
        }
        if (!remissionsAppliedForRefund.isEmpty() && !remissionsAppliedForRefund.get(0).getHwfAmount().equals(refund.getAmount())) {
            throw new UnequalRemissionAmountWithRefundRaisedException("Remission amount not equal to refund amount");
        }
    }

    private List<PaymentFeeResponse> getRetrospectiveRemissionAppliedFee(PaymentGroupResponse paymentGroupResponse, List<Integer> refundFeeIds) {
        List<PaymentFeeResponse> feeResponses = paymentGroupResponse.getFees().stream()
            .filter(feeResponse -> feeResponse.getId().equals(refundFeeIds.get(0))).collect(
            Collectors.toList());
        if (feeResponses.isEmpty()) {
            throw new RefundFeeNotFoundInPaymentException("Refund not found in payment");
        }
        return feeResponses;
    }

    private String getDate(Timestamp timestamp) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'hh:mm:ss", Locale.ENGLISH);
        return simpleDateFormat.format(timestamp);

    }

    private String getRefundReason(String rawReason) {
        if (rawReason.startsWith("RR")) {
            Optional<RefundReason> refundReasonOptional = refundReasonRepository.findByCode(rawReason);
            if (refundReasonOptional.isPresent()) {
                return refundReasonOptional.get().getName();
            }
            throw new RefundReasonNotFoundException(rawReason);
        }
        return rawReason;
    }
}
