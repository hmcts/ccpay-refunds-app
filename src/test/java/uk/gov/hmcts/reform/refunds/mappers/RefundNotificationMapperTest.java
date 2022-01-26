package uk.gov.hmcts.reform.refunds.mappers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType;
import uk.gov.hmcts.reform.refunds.dtos.requests.RecipientPostalAddress;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationEmailRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationLetterRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResendNotificationRequest;
import uk.gov.hmcts.reform.refunds.mapper.RefundNotificationMapper;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@ActiveProfiles({"local", "test"})
@SpringBootTest(webEnvironment = MOCK)
class RefundNotificationMapperTest {

    private static final Refund REFUND = Refund.refundsWith()
            .id(1)
            .refundStatus(RefundStatus.SENTFORAPPROVAL)
            .ccdCaseNumber("1234567812345678")
            .reference("RF-1642-6117-6119-7355")
            .build();

    private static final ResendNotificationRequest RESEND_NOTIFICATION_EMAIL_REQUEST = ResendNotificationRequest.resendNotificationRequest()
            .reference("RF-1642-6117-6119-7355")
            .recipientEmailAddress("abc@abc.com")
            .build();

    private static final ResendNotificationRequest RESEND_NOTIFICATION_LETTER_REQUEST = ResendNotificationRequest.resendNotificationRequest()
            .reference("RF-1642-6117-6119-7355")
            .recipientPostalAddress(RecipientPostalAddress.recipientPostalAddressWith()
                    .addressLine("ADDRESS LINE 1")
                    .city("LONDON")
                    .county("LONDONSHIRE")
                    .country("UNITED KINGDOM")
                    .postalCode("ED11 1ED")
                    .build())
            .build();

    @Autowired
    private RefundNotificationMapper refundNotificationMapper;

    @Test
    void givenResendNotificationEmailRequest_whenGetRefundNotificationEmailRequest_thenRefundNotificationEmailRequestIsReceived() {

        RefundNotificationEmailRequest refundNotificationEmailRequest =
                refundNotificationMapper.getRefundNotificationEmailRequest(REFUND, RESEND_NOTIFICATION_EMAIL_REQUEST);

        assertNotNull(refundNotificationEmailRequest);
        assertEquals(NotificationType.EMAIL, refundNotificationEmailRequest.getNotificationType());
        assertEquals("abc@abc.com", refundNotificationEmailRequest.getRecipientEmailAddress());
        assertEquals("1234567812345678", refundNotificationEmailRequest.getPersonalisation().getCcdCaseNumber());
        assertEquals("RF-1642-6117-6119-7355", refundNotificationEmailRequest.getPersonalisation().getRefundReference());
    }

    @Test
    void givenResendNotificationLetterRequest_whenGetRefundNotificationLetterRequest_thenRefundNotificationLetterRequestIsReceived() {

        RefundNotificationLetterRequest refundNotificationLetterRequest =
                refundNotificationMapper.getRefundNotificationLetterRequest(REFUND, RESEND_NOTIFICATION_LETTER_REQUEST);

        assertNotNull(refundNotificationLetterRequest);
        assertEquals(NotificationType.LETTER, refundNotificationLetterRequest.getNotificationType());
        assertEquals("ED11 1ED", refundNotificationLetterRequest.getRecipientPostalAddress().getPostalCode());
        assertEquals("1234567812345678", refundNotificationLetterRequest.getPersonalisation().getCcdCaseNumber());
        assertEquals("RF-1642-6117-6119-7355", refundNotificationLetterRequest.getPersonalisation().getRefundReference());
    }
}
