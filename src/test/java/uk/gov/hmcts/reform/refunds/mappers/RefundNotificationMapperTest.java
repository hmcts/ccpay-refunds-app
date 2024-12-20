package uk.gov.hmcts.reform.refunds.mappers;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType;
import uk.gov.hmcts.reform.refunds.dtos.requests.FromTemplateContact;
import uk.gov.hmcts.reform.refunds.dtos.requests.MailAddress;
import uk.gov.hmcts.reform.refunds.dtos.requests.RecipientPostalAddress;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationEmailRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationLetterRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResendNotificationRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.TemplatePreview;
import uk.gov.hmcts.reform.refunds.mapper.RefundNotificationMapper;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;

@ActiveProfiles({"local", "test"})
@SpringBootTest(webEnvironment = MOCK)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class RefundNotificationMapperTest {

    private static final Refund REFUND = Refund.refundsWith()
            .id(1)
            .refundStatus(RefundStatus.SENTFORAPPROVAL)
            .ccdCaseNumber("1234567812345678")
            .reference("RF-1642-6117-6119-7355")
            .build();

    private static final Refund REFUND_Email = Refund.refundsWith()
        .id(1)
        .refundStatus(RefundStatus.SENTFORAPPROVAL)
        .ccdCaseNumber("1234567812345678")
        .reference("RF-1642-6117-6119-7355")
        .contactDetails(ContactDetails.contactDetailsWith()
                            .email("abc@abc.com")
                            .notificationType("EMAIL")
                            .build())
        .build();

    private static final Refund REFUND_letter = Refund.refundsWith()
        .id(1)
        .refundStatus(RefundStatus.SENTFORAPPROVAL)
        .ccdCaseNumber("1234567812345678")
        .reference("RF-1642-6117-6119-7355")
        .contactDetails(ContactDetails.contactDetailsWith()
                            .addressLine("ABC Street")
                            .city("London")
                            .county("Greater London")
                            .country("UK")
                            .postalCode("E1 6AN")
                            .notificationType("LETTER")
                            .build())
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

    private static final String CUSTOMER_REFERENCE = "1234567890";
    @Autowired
    private RefundNotificationMapper refundNotificationMapper;

    @Test
    void givenResendNotificationEmailRequest_whenGetRefundNotificationEmailRequest_thenRefundNotificationEmailRequestIsReceived() {

        RefundNotificationEmailRequest refundNotificationEmailRequest =
                refundNotificationMapper.getRefundNotificationEmailRequest(REFUND, RESEND_NOTIFICATION_EMAIL_REQUEST, CUSTOMER_REFERENCE);

        assertNotNull(refundNotificationEmailRequest);
        assertEquals(NotificationType.EMAIL, refundNotificationEmailRequest.getNotificationType());
        assertEquals("abc@abc.com", refundNotificationEmailRequest.getRecipientEmailAddress());
        assertEquals("1234567812345678", refundNotificationEmailRequest.getPersonalisation().getCcdCaseNumber());
        assertEquals("RF-1642-6117-6119-7355", refundNotificationEmailRequest.getPersonalisation().getRefundReference());
        assertEquals("1234567890", refundNotificationEmailRequest.getPersonalisation().getCustomerReference());
    }

    @Test
    void givenResendNotificationLetterRequest_whenGetRefundNotificationLetterRequest_thenRefundNotificationLetterRequestIsReceived() {

        RefundNotificationLetterRequest refundNotificationLetterRequest =
                refundNotificationMapper.getRefundNotificationLetterRequest(REFUND, RESEND_NOTIFICATION_LETTER_REQUEST, CUSTOMER_REFERENCE);

        assertNotNull(refundNotificationLetterRequest);
        assertEquals(NotificationType.LETTER, refundNotificationLetterRequest.getNotificationType());
        assertEquals("ED11 1ED", refundNotificationLetterRequest.getRecipientPostalAddress().getPostalCode());
        assertEquals("1234567812345678", refundNotificationLetterRequest.getPersonalisation().getCcdCaseNumber());
        assertEquals("RF-1642-6117-6119-7355", refundNotificationLetterRequest.getPersonalisation().getRefundReference());
        assertEquals("1234567890", refundNotificationLetterRequest.getPersonalisation().getCustomerReference());
    }

    @Test
    void givenResendNotificationEmailRequest_whenGetRefundNotificationEmailRequest_thenRefundNotificationEmailRequestIsReceived_approvalJourney() {

        RefundNotificationEmailRequest refundNotificationEmailRequest =
            refundNotificationMapper.getRefundNotificationEmailRequestApproveJourney(REFUND_Email, CUSTOMER_REFERENCE);

        assertNotNull(refundNotificationEmailRequest);
        assertEquals(NotificationType.EMAIL, refundNotificationEmailRequest.getNotificationType());
        assertEquals("abc@abc.com", refundNotificationEmailRequest.getRecipientEmailAddress());
        assertEquals("1234567812345678", refundNotificationEmailRequest.getPersonalisation().getCcdCaseNumber());
        assertEquals("RF-1642-6117-6119-7355", refundNotificationEmailRequest.getPersonalisation().getRefundReference());
        assertEquals("1234567890", refundNotificationEmailRequest.getPersonalisation().getCustomerReference());
    }

    @Test
    void testGetRefundNotificationEmailRequestApproveJourneyWithTemplatePreviewIsNotNull() {

        TemplatePreview templatePreview = TemplatePreview.templatePreviewWith()
            .id(UUID.randomUUID())
            .templateType("email")
            .version(11)
            .body("Dear Sir Madam")
            .subject("HMCTS refund request approved")
            .html("Dear Sir Madam")
            .from(FromTemplateContact
                      .buildFromTemplateContactWith()
                      .fromEmailAddress("test@test.com")
                      .build())
            .build();

        RefundNotificationEmailRequest refundNotificationEmailRequest =
            refundNotificationMapper.getRefundNotificationEmailRequestApproveJourney(REFUND_Email, templatePreview, "Template-1", CUSTOMER_REFERENCE);

        assertNotNull(refundNotificationEmailRequest);
        assertEquals(NotificationType.EMAIL, refundNotificationEmailRequest.getNotificationType());
        assertEquals("abc@abc.com", refundNotificationEmailRequest.getRecipientEmailAddress());
        assertEquals("1234567812345678", refundNotificationEmailRequest.getPersonalisation().getCcdCaseNumber());
        assertEquals("RF-1642-6117-6119-7355", refundNotificationEmailRequest.getPersonalisation().getRefundReference());
        assertEquals("1234567890", refundNotificationEmailRequest.getPersonalisation().getCustomerReference());
        assertNotNull(refundNotificationEmailRequest.getTemplatePreview());
        assertEquals("email", refundNotificationEmailRequest.getTemplatePreview().getTemplateType());
        assertEquals("11", "" + refundNotificationEmailRequest.getTemplatePreview().getVersion());
        assertEquals("Dear Sir Madam", refundNotificationEmailRequest.getTemplatePreview().getBody());
        assertEquals("HMCTS refund request approved", refundNotificationEmailRequest.getTemplatePreview().getSubject());
        assertEquals("Dear Sir Madam", refundNotificationEmailRequest.getTemplatePreview().getHtml());
    }

    @Test
    void testGetRefundNotificationEmailRequestApproveJourneyWithTemplatePreviewIsNull() {


        RefundNotificationEmailRequest refundNotificationEmailRequest =
            refundNotificationMapper.getRefundNotificationEmailRequestApproveJourney(REFUND_Email, null, null, CUSTOMER_REFERENCE);

        assertNotNull(refundNotificationEmailRequest);
        assertEquals(NotificationType.EMAIL, refundNotificationEmailRequest.getNotificationType());
        assertEquals("abc@abc.com", refundNotificationEmailRequest.getRecipientEmailAddress());
        assertEquals("1234567812345678", refundNotificationEmailRequest.getPersonalisation().getCcdCaseNumber());
        assertEquals("RF-1642-6117-6119-7355", refundNotificationEmailRequest.getPersonalisation().getRefundReference());
        assertEquals("1234567890", refundNotificationEmailRequest.getPersonalisation().getCustomerReference());
        assertNull(refundNotificationEmailRequest.getTemplatePreview());
    }

    @Test
    void givenResendNotificationLetterRequest_whenGetRefundNotificationLetterRequest_thenRefundNotificationLetterRequestIsReceived_approvalJourney() {

        RefundNotificationLetterRequest refundNotificationLetterRequest =
            refundNotificationMapper.getRefundNotificationLetterRequestApproveJourney(REFUND_letter, CUSTOMER_REFERENCE);

        assertNotNull(refundNotificationLetterRequest);
        assertEquals(NotificationType.LETTER, refundNotificationLetterRequest.getNotificationType());
        assertEquals("E1 6AN", refundNotificationLetterRequest.getRecipientPostalAddress().getPostalCode());
        assertEquals("1234567812345678", refundNotificationLetterRequest.getPersonalisation().getCcdCaseNumber());
        assertEquals("RF-1642-6117-6119-7355", refundNotificationLetterRequest.getPersonalisation().getRefundReference());
        assertEquals("1234567890", refundNotificationLetterRequest.getPersonalisation().getCustomerReference());
    }

    @Test
    void testGetRefundNotificationLetterRequestApproveJourneyWithTemplatePreviewIsNotNull() {

        TemplatePreview templatePreview = TemplatePreview.templatePreviewWith()
            .id(UUID.randomUUID())
            .templateType("email")
            .version(11)
            .body("Dear Sir Madam")
            .subject("HMCTS refund request approved")
            .html("Dear Sir Madam")
            .from(FromTemplateContact
                      .buildFromTemplateContactWith()
                      .fromMailAddress(
                          MailAddress
                              .buildRecipientMailAddressWith()
                              .addressLine("6 Test")
                              .city("city")
                              .country("country")
                              .county("county")
                              .postalCode("HA3 5TT")
                              .build())
                      .build())
            .build();

        RefundNotificationLetterRequest refundNotificationLetterRequest =
            refundNotificationMapper.getRefundNotificationLetterRequestApproveJourney(REFUND_letter, templatePreview, null, CUSTOMER_REFERENCE);

        assertNotNull(refundNotificationLetterRequest);
        assertEquals(NotificationType.LETTER, refundNotificationLetterRequest.getNotificationType());
        assertEquals("E1 6AN", refundNotificationLetterRequest.getRecipientPostalAddress().getPostalCode());
        assertEquals("1234567812345678", refundNotificationLetterRequest.getPersonalisation().getCcdCaseNumber());
        assertEquals("RF-1642-6117-6119-7355", refundNotificationLetterRequest.getPersonalisation().getRefundReference());
        assertEquals("1234567890", refundNotificationLetterRequest.getPersonalisation().getCustomerReference());
        assertNotNull(refundNotificationLetterRequest.getTemplatePreview());
        assertEquals("email", refundNotificationLetterRequest.getTemplatePreview().getTemplateType());
        assertEquals("11", "" + refundNotificationLetterRequest.getTemplatePreview().getVersion());
        assertEquals("Dear Sir Madam", refundNotificationLetterRequest.getTemplatePreview().getBody());
        assertEquals("HMCTS refund request approved", refundNotificationLetterRequest.getTemplatePreview().getSubject());
        assertEquals("Dear Sir Madam", refundNotificationLetterRequest.getTemplatePreview().getHtml());
    }

    @Test
    void tesGetRefundNotificationLetterRequestApproveJourneyWithTemplatePreviewIsNull() {

        RefundNotificationLetterRequest refundNotificationLetterRequest =
            refundNotificationMapper.getRefundNotificationLetterRequestApproveJourney(REFUND_letter, null, null, CUSTOMER_REFERENCE);

        assertNotNull(refundNotificationLetterRequest);
        assertEquals(NotificationType.LETTER, refundNotificationLetterRequest.getNotificationType());
        assertEquals("E1 6AN", refundNotificationLetterRequest.getRecipientPostalAddress().getPostalCode());
        assertEquals("1234567812345678", refundNotificationLetterRequest.getPersonalisation().getCcdCaseNumber());
        assertEquals("RF-1642-6117-6119-7355", refundNotificationLetterRequest.getPersonalisation().getRefundReference());
        assertEquals("1234567890", refundNotificationLetterRequest.getPersonalisation().getCustomerReference());
        assertNull(refundNotificationLetterRequest.getTemplatePreview());
    }
}
