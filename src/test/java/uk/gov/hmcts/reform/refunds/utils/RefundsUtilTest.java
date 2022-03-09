package uk.gov.hmcts.reform.refunds.utils;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.refunds.exceptions.PaymentServerException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType.EMAIL;
import static uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType.LETTER;

@ActiveProfiles({"local", "test"})
@SpringBootTest(webEnvironment = MOCK)
public class RefundsUtilTest {

    @Value("${notify.template.cheque-po-cash.letter}")
    private String chequePoCashLetterTemplateId;

    @Value("${notify.template.cheque-po-cash.email}")
    private String chequePoCashEmailTemplateId;

    @Value("${notify.template.card-pba.letter}")
    private String cardPbaLetterTemplateId;

    @Value("${notify.template.card-pba.email}")
    private String cardPbaEmailTemplateId;

    @Autowired
    RefundsUtil util;

    @Test
    void givenNullMethodAndNotificationType_whenGetTemplate_thenPaymentServerExceptionIsReceived() {
        Exception exception = assertThrows(PaymentServerException.class, () -> util.getTemplate(null, null));
        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("No valid payment method found"));
    }

    @Test
    void givenChequeEmail_whenGetTemplate_thenTemplateIdIsReceived() {
        String result = util.getTemplate("cheque", EMAIL.toString());
        assertEquals(chequePoCashEmailTemplateId, result);
    }

    @Test
    void givenCashLetter_whenGetTemplate_thenTemplateIdIsReceived() {
        String result = util.getTemplate("cash", LETTER.toString());
        assertEquals(chequePoCashLetterTemplateId, result);
    }

    @Test
    void givenCardEmail_whenGetTemplate_thenTemplateIdIsReceived() {
        String result = util.getTemplate("card", EMAIL.toString());
        assertEquals(cardPbaEmailTemplateId, result);
    }

    @Test
    void givenPbaLetter_whenGetTemplate_thenTemplateIdIsReceived() {
        String result = util.getTemplate("payment by account", LETTER.toString());
        assertEquals(cardPbaLetterTemplateId, result);
    }
}