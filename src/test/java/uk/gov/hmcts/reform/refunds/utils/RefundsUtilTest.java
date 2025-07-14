package uk.gov.hmcts.reform.refunds.utils;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType.EMAIL;
import static uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType.LETTER;

@ActiveProfiles({"local", "test"})
@SpringBootTest(webEnvironment = MOCK)
class RefundsUtilTest {

    @Autowired
    RefundsUtil util;

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

    private static final Refund REFUND = Refund.refundsWith()
            .id(1)
        .amount(BigDecimal.valueOf(100))
            .ccdCaseNumber("1111111111111111")
        .createdBy("AAAA")
        .reference("RF-1111-2222-3333-4444")
        .refundStatus(RefundStatus.SENTFORAPPROVAL)
        .paymentReference("RC-1111-2234-1077-1123")
        .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
            .updatedBy("BBBB")
        .contactDetails(ContactDetails.contactDetailsWith()
                            .addressLine("aaaa")
                            .city("bbbb")
                            .country("cccc")
                            .county("dddd")
                            .build())
            .build();

    private Refund getRefund(Refund refund, String instructionType, String notificationType) {
        refund.setRefundInstructionType(instructionType);
        refund.getContactDetails().setNotificationType(notificationType);
        refund.setReason("RR001");
        return refund;
    }

    private Refund getRefund(Refund refund, String instructionType, String notificationType, String reason) {
        refund = getRefund(refund, instructionType, notificationType);
        refund.setReason(reason);
        return refund;
    }

    @Test
    void givenChequeEmail_whenGetTemplate_thenTemplateIdIsReceived() {
        String result = util.getTemplate(getRefund(REFUND,"RefundWhenContacted", EMAIL.toString()));
        assertEquals(chequePoCashEmailTemplateId, result);
    }

    @Test
    void givenCashLetter_whenGetTemplate_thenTemplateIdIsReceived() {
        String result = util.getTemplate(getRefund(REFUND,"RefundWhenContacted", LETTER.toString()));
        assertEquals(chequePoCashLetterTemplateId, result);
    }

    @Test
    void givenCardEmail_whenGetTemplate_thenTemplateIdIsReceived() {
        Refund refund = getRefund(REFUND,"SendRefund", EMAIL.toString());
        String result = util.getTemplate(refund);
        assertEquals(cardPbaEmailTemplateId, result);
    }

    @Test
    void givenPbaLetter_whenGetTemplate_thenTemplateIdIsReceived() {
        String result = util.getTemplate(getRefund(REFUND,"SendRefund", LETTER.toString()));
        assertEquals(cardPbaLetterTemplateId, result);
    }
}
