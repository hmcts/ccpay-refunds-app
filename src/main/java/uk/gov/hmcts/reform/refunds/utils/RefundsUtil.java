package uk.gov.hmcts.reform.refunds.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentFeeResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentGroupResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RemissionResponse;
import uk.gov.hmcts.reform.refunds.exceptions.RefundFeeNotFoundInPaymentException;
import uk.gov.hmcts.reform.refunds.exceptions.RetrospectiveRemissionNotFoundException;
import uk.gov.hmcts.reform.refunds.exceptions.UnequalRemissionAmountWithRefundRaisedException;
import uk.gov.hmcts.reform.refunds.model.Refund;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType.EMAIL;

@Component
public class RefundsUtil {

    private static final Logger LOG = LoggerFactory.getLogger(RefundsUtil.class);

    @Value("${notify.template.cheque-po-cash.letter}")
    private String chequePoCashLetterTemplateId;

    @Value("${notify.template.cheque-po-cash.email}")
    private String chequePoCashEmailTemplateId;

    @Value("${notify.template.card-pba.letter}")
    private String cardPbaLetterTemplateId;

    @Value("${notify.template.card-pba.email}")
    private String cardPbaEmailTemplateId;

    @Value("${notify.template.refund-when-contacted.email}")
    private String refundWhenContactedEmailTemplateId;

    @Value("${notify.template.refund-when-contacted.letter}")
    private String refundWhenContactedLetterTemplateId;

    public static final String REFUND_WHEN_CONTACTED = "RefundWhenContacted";

    public static final String REFUND_WHEN_CONTACTED_REJECT_REASON = "Unable to apply refund to Card";

    public String getTemplate(Refund refund) {
        return getTemplate(refund, refund.getReason());
    }

    public String getTemplate(Refund refund, String reason) {
        String templateId = null;
        if (null != refund.getRefundInstructionType()) {

            if (REFUND_WHEN_CONTACTED.equals(refund.getRefundInstructionType())) {
                if (REFUND_WHEN_CONTACTED_REJECT_REASON.equalsIgnoreCase(reason)) {
                    if (EMAIL.name().equals(refund.getContactDetails().getNotificationType())) {
                        templateId = refundWhenContactedEmailTemplateId;
                    } else {
                        templateId = refundWhenContactedLetterTemplateId;
                    }
                } else if (EMAIL.name().equals(refund.getContactDetails().getNotificationType())) {
                    templateId = chequePoCashEmailTemplateId;
                } else {
                    templateId = chequePoCashLetterTemplateId;
                }
            } else {
                if (EMAIL.name().equals(refund.getContactDetails().getNotificationType())) {
                    templateId = cardPbaEmailTemplateId;
                } else {
                    templateId = cardPbaLetterTemplateId;
                }
            }
        }
        return templateId;
    }

    public void logPaymentDto(PaymentGroupResponse paymentDto) {
        if (paymentDto != null) {
            LOG.info("paymentDto.getPayments(): {}", paymentDto.getPayments());
            if (paymentDto.getPayments() != null
                    && !paymentDto.getPayments().isEmpty()) {
                LOG.info("paymentDto.getPayments().get(0): {}", paymentDto.getPayments().get(0));
            }
        }
    }

    public void validateRefundRequestFees(Refund refund, PaymentGroupResponse paymentGroupResponse) {
        String feeIds = refund.getFeeIds();
        List<Integer> refundFeeIds = getRefundFeeIds(feeIds);
        if (!refundFeeIds.isEmpty()) {
            List<RemissionResponse> remissionsAppliedForRefund = paymentGroupResponse.getRemissions().stream()
                    .filter(remissionResponse -> refundFeeIds.contains(remissionResponse.getFeeId())).collect(
                            Collectors.toList());
            if (refund.getReason().equals("RR036")) {
                // create a constant and add the code
                validateRetrospectiveRemissions(remissionsAppliedForRefund,refund);
                getRetrospectiveRemissionAppliedFee(paymentGroupResponse, refundFeeIds);
            }

        }
    }

    private List<Integer> getRefundFeeIds(String refundIds) {
        return  Arrays.stream(refundIds.split(",")).map(Integer::parseInt).collect(Collectors.toList());
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

}
