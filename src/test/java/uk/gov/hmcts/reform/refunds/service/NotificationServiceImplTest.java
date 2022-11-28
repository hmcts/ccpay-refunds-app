package uk.gov.hmcts.reform.refunds.service;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType;
import uk.gov.hmcts.reform.refunds.dtos.requests.Personalisation;
import uk.gov.hmcts.reform.refunds.dtos.requests.RecipientPostalAddress;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationEmailRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationLetterRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.TemplatePreview;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundNotificationResendRequestException;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.services.NotificationService;
import uk.gov.hmcts.reform.refunds.utils.RefundsUtil;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

@SpringBootTest(webEnvironment = MOCK)
@ActiveProfiles({"local", "test"})
class NotificationServiceImplTest {

    @Autowired
    private NotificationService notificationService;

    @MockBean
    private AuthTokenGenerator authTokenGenerator;

    @MockBean
    private RefundsRepository refundsRepository;

    @MockBean
    @Qualifier("restTemplateNotify")
    private RestTemplate restTemplateNotify;

    @Test
    void postEmailNotificationDataShouldReturnSuccessfulStatus_WhenNotificationServiceIsAvailable() {
        when(restTemplateNotify.exchange(anyString(),
                                         Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class), eq(String.class))).thenReturn(
            new ResponseEntity<>("Success", HttpStatus.OK)
        );
        ResponseEntity<String> responseEntity = notificationService.postEmailNotificationData(getHeaders(),getRefundNotificationEmailRequest());
        assertEquals(responseEntity.getStatusCode(), HttpStatus.OK);

    }

    @Test
    void postLetterNotificationDataShouldReturnSuccessfulStatus_WhenNotificationServiceIsAvailable() {
        when(restTemplateNotify.exchange(anyString(),
                                         Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class), eq(String.class))).thenReturn(
            new ResponseEntity<>("Success", HttpStatus.OK)
        );
        ResponseEntity<String> responseEntity = notificationService.postLetterNotificationData(getHeaders(),getRefundNotificationLetterRequest());
        assertEquals(responseEntity.getStatusCode(), HttpStatus.OK);
    }

    @Test
    void postEmailNotificationDataShouldReturnUnavailableStatus_WhenNotificationServiceIsUnAvailable() {
        when(restTemplateNotify.exchange(anyString(),
                                         Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class), eq(String.class))).thenThrow(
            new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE)
        );
        ResponseEntity<String> responseEntity = notificationService.postEmailNotificationData(getHeaders(),getRefundNotificationEmailRequest());
        assertEquals(responseEntity.getStatusCode(), HttpStatus.SERVICE_UNAVAILABLE);

    }

    @Test
    void postLetterNotificationDataShouldReturnUnavailableStatus_WhenNotificationServiceIsUnAvailable() {
        when(restTemplateNotify.exchange(anyString(),
                                         Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class), eq(String.class))).thenThrow(
            new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE)
        );
        ResponseEntity<String> responseEntity = notificationService.postLetterNotificationData(getHeaders(),getRefundNotificationLetterRequest());
        assertEquals(responseEntity.getStatusCode(), HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void postEmailNotificationDataShouldReturnInvalidRequest_WhenSendingMalformedRequest() {
        when(restTemplateNotify.exchange(anyString(),
                                         Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class), eq(String.class))).thenThrow(
            new HttpClientErrorException(HttpStatus.BAD_REQUEST)
        );
        assertThrows(
            InvalidRefundNotificationResendRequestException.class,
            () -> notificationService.postEmailNotificationData(getHeaders(), getRefundNotificationEmailRequest()));

    }

    @Test
    void postLetterNotificationDataShouldReturnInvalidRequest_WhenSendingMalformedRequest() {
        when(restTemplateNotify.exchange(anyString(),
                                         Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class), eq(String.class))).thenThrow(
            new HttpClientErrorException(HttpStatus.BAD_REQUEST)
        );
        assertThrows(
            InvalidRefundNotificationResendRequestException.class,
            () -> notificationService.postLetterNotificationData(getHeaders(), getRefundNotificationLetterRequest()));
    }

    private Refund getRefund() {
        return Refund.refundsWith()
            .id(1)
            .amount(BigDecimal.valueOf(100))
            .reason("RR0001")
            .reference("RF-1628-5241-9956-2215")
            .paymentReference("RC-1628-5241-9956-2315")
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
            .refundStatus(uk.gov.hmcts.reform.refunds.model.RefundStatus.SENTFORAPPROVAL)
            .createdBy("6463ca66-a2e5-4f9f-af95-653d4dd4a79c")
            .updatedBy("6463ca66-a2e5-4f9f-af95-653d4dd4a79c")
            .feeIds("50")
            .contactDetails(ContactDetails.contactDetailsWith()
                                .email("abc@abc.com")
                                .notificationType("EMAIL")
                                .build())
            .statusHistories(Arrays.asList(StatusHistory.statusHistoryWith()
                                               .id(1)
                                               .status(RefundStatus.SENTFORAPPROVAL.getName())
                                               .createdBy("6463ca66-a2e5-4f9f-af95-653d4dd4a79c")
                                               .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
                                               .notes("Refund initiated and sent to team leader")
                                               .build()))
            .build();
    }

    private TemplatePreview getTemplatePreview() {
        return TemplatePreview.templatePreviewWith()
            .id(UUID.randomUUID())
            .templateType("email")
            .version(11)
            .body("Dear Sir Madam")
            .subject("HMCTS refund request approved")
            .html("Dear Sir Madam")
            .build();
    }

    @Test
    void updateNotificationWith2xxCode() {
        Refund refund = getRefund();
        refund.setRefundStatus(RefundStatus.REJECTED);
        refund.setReason(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON);
        when(restTemplateNotify.exchange(anyString(),
                                         Mockito.any(HttpMethod.class),
                                         Mockito.any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("Success", HttpStatus.OK));

        when(refundsRepository.save(any(Refund.class))).thenReturn(refund);

        notificationService.updateNotification(getHeaders(),refund);

        assertEquals("SENT",refund.getNotificationSentFlag());
    }

    @Test
    void updateNotificationEmailWith5xxCode() {
        Refund refund = getRefund();
        refund.setRefundStatus(RefundStatus.REJECTED);
        refund.setReason(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON);
        when(restTemplateNotify.exchange(anyString(),
                                         Mockito.any(HttpMethod.class),
                                         Mockito.any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR));

        when(refundsRepository.save(any(Refund.class))).thenReturn(refund);

        notificationService.updateNotification(getHeaders(),refund);

        assertEquals("EMAIL_NOT_SENT",refund.getNotificationSentFlag());
    }

    @Test
    void updateNotificationLetterWith5xxCode() {

        Refund refund = getRefund();
        refund.setContactDetails(ContactDetails.contactDetailsWith()
                               .addressLine("ABC Street")
                               .city("London")
                               .county("Greater London")
                               .country("UK")
                               .postalCode("E1 6AN")
                               .notificationType("LETTER")
                               .build());

        refund.setRefundStatus(RefundStatus.REJECTED);
        refund.setReason(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON);
        when(restTemplateNotify.exchange(anyString(),
                                         Mockito.any(HttpMethod.class),
                                         Mockito.any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("Internal Server Error", HttpStatus.INTERNAL_SERVER_ERROR));

        when(refundsRepository.save(any(Refund.class))).thenReturn(refund);

        notificationService.updateNotification(getHeaders(),refund);

        assertEquals("LETTER_NOT_SENT",refund.getNotificationSentFlag());
    }

    @Test
    void updateNotificationWithErrorCode() {
        Refund refund = getRefund();
        refund.setRefundStatus(RefundStatus.REJECTED);
        refund.setReason(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON);
        when(restTemplateNotify.exchange(anyString(),
                                         Mockito.any(HttpMethod.class),
                                         Mockito.any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("Bed request", HttpStatus.BAD_REQUEST));

        when(refundsRepository.save(any(Refund.class))).thenReturn(refund);

        notificationService.updateNotification(getHeaders(),refund);

        assertEquals("ERROR",refund.getNotificationSentFlag());
    }

    @Test
    void updateNotificationSuccessWithTemplatePreview() {

        Refund refund = getRefund();
        refund.setRefundStatus(RefundStatus.REJECTED);
        refund.setReason(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON);
        when(restTemplateNotify.exchange(anyString(),
                                         Mockito.any(HttpMethod.class),
                                         Mockito.any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("Success", HttpStatus.OK));

        when(refundsRepository.save(any(Refund.class))).thenReturn(refund);

        notificationService.updateNotification(getHeaders(),refund, getTemplatePreview());

        assertEquals("SENT",refund.getNotificationSentFlag());
    }

    @Test
    void updateNotificationSuccessWithTemplatePreviewNull() {

        Refund refund = getRefund();
        refund.setRefundStatus(RefundStatus.REJECTED);
        refund.setReason(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON);
        when(restTemplateNotify.exchange(anyString(),
                                         Mockito.any(HttpMethod.class),
                                         Mockito.any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("Success", HttpStatus.OK));

        when(refundsRepository.save(any(Refund.class))).thenReturn(refund);

        notificationService.updateNotification(getHeaders(),refund, null);

        assertEquals("SENT",refund.getNotificationSentFlag());
    }


    private MultiValueMap<String,String> getHeaders() {
        MultiValueMap<String, String> inputHeaders = new LinkedMultiValueMap<>();
        inputHeaders.put("Authorization", Collections.singletonList("authToken"));
        inputHeaders.put("content-type", Collections.singletonList("application/json"));
        inputHeaders.put("ServiceAuthorization", Collections.singletonList("servAuthToken"));
        return inputHeaders;
    }

    private RefundNotificationEmailRequest getRefundNotificationEmailRequest() {
        return RefundNotificationEmailRequest.refundNotificationEmailRequestWith()
            .notificationType(NotificationType.EMAIL)
            .reference("RF-1234-1234-1234")
            .personalisation(
                Personalisation.personalisationRequestWith()
                    .refundReference("RF-1234-1234-1234")
                    .build())
            .emailReplyToId("mockmail@mock.com")
            .templateId("TEMP-123")
            .serviceName("Probate")
            .build();
    }


    private RefundNotificationLetterRequest getRefundNotificationLetterRequest() {
        return RefundNotificationLetterRequest.refundNotificationLetterRequestWith()
            .notificationType(NotificationType.LETTER)
            .reference("RF-1234-1234-1234")
            .personalisation(
                    Personalisation.personalisationRequestWith()
                        .refundReference("RF-1234-1234-1234")
                        .build())
            .recipientPostalAddress(RecipientPostalAddress.recipientPostalAddressWith()
                                        .addressLine("PB 123")
                                        .city("Nottingham")
                                        .postalCode("NG2 2DA")
                                        .build())
            .templateId("TEMP-123")
            .serviceName("Probate")
            .build();
    }
}
