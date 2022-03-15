package uk.gov.hmcts.reform.refunds.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.model.Refund;

import static uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType.EMAIL;

@Component
public class RefundsUtil {

    @Value("${notify.template.cheque-po-cash.letter}")
    private String chequePoCashLetterTemplateId;

    @Value("${notify.template.cheque-po-cash.email}")
    private String chequePoCashEmailTemplateId;

    @Value("${notify.template.card-pba.letter}")
    private String cardPbaLetterTemplateId;

    @Value("${notify.template.card-pba.email}")
    private String cardPbaEmailTemplateId;

    public String getTemplate(Refund refund) {
        String templateId = null;
        if (null != refund.getRefundInstructionType()) {

            if ("RefundWhenContacted".equals(refund.getRefundInstructionType())) {
                if (EMAIL.name().equals(refund.getContactDetails().getNotificationType())) {
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
}