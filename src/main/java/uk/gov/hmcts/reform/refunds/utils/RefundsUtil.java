package uk.gov.hmcts.reform.refunds.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.refunds.exceptions.PaymentServerException;

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

    private static final String CHEQUE = "cheque";

    private static final String CASH = "cash";

    private static final String CARD = "card";

    public String getTemplate(String method, String notificationType) {
        if (null != method && null != notificationType) {
            if (CHEQUE.equals(method) || method.contains("postal") || CASH.equals(method)) {
                if (EMAIL.toString().equals(notificationType)) {
                    return chequePoCashEmailTemplateId;
                } else {
                    return chequePoCashLetterTemplateId;
                }
            } else if (CARD.equals(method) || method.contains("payment")) {
                if (EMAIL.toString().equals(notificationType)) {
                    return cardPbaEmailTemplateId;
                } else {
                    return cardPbaLetterTemplateId;
                }
            }
        }
        throw new PaymentServerException("No valid payment method found");
    }
}
