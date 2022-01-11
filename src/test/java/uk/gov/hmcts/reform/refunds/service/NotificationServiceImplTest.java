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
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundNotificationResendRequestException;
import uk.gov.hmcts.reform.refunds.services.NotificationService;

import java.util.Arrays;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

@SpringBootTest(webEnvironment = MOCK)
@ActiveProfiles({"local", "test"})
public class NotificationServiceImplTest {

    @Autowired
    private NotificationService notificationService;

    @MockBean
    private AuthTokenGenerator authTokenGenerator;

    @MockBean
    @Qualifier("restTemplateNotify")
    private RestTemplate restTemplateNotify;

    @Test
    void postEmailNotificationDataShouldReturnSuccessfulStatus_WhenNotificationServiceIsAvailable() {
        when(restTemplateNotify.exchange(anyString(),
                                         Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class), eq(String.class))).thenReturn(
            new ResponseEntity<String>("Success", HttpStatus.OK)
        );
        ResponseEntity<String> responseEntity = notificationService.postEmailNotificationData(getHeaders(),getRefundNotificationEmailRequest());
        assertEquals(responseEntity.getStatusCode(), HttpStatus.OK);

    }

    @Test
    void postLetterNotificationDataShouldReturnSuccessfulStatus_WhenNotificationServiceIsAvailable() {
        when(restTemplateNotify.exchange(anyString(),
                                         Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class), eq(String.class))).thenReturn(
            new ResponseEntity<String>("Success", HttpStatus.OK)
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




    private MultiValueMap<String,String> getHeaders() {
        MultiValueMap<String, String> inputHeaders = new LinkedMultiValueMap<>();
        inputHeaders.put("Authorization", Arrays.asList("authtoken"));
        inputHeaders.put("content-type", Arrays.asList("application/json"));
        inputHeaders.put("ServiceAuthorization", Arrays.asList("servauthtoken"));
        return inputHeaders;
    }

    private RefundNotificationEmailRequest getRefundNotificationEmailRequest() {
        return RefundNotificationEmailRequest.refundNotificationEmailRequestWith()
            .notificationType(NotificationType.EMAIL)
            .reference("RF-1234-1234-1234")
            .personalisation(
                Personalisation.personalisationRequestWith()
                    .serviceUrl("service@gmail.com")
                    .serviceMailBox("service")
                    .refundReference("RF-1234-1234-1234")
                    .build())
            .emailReplyToId("mockmail@mock.com")
            .templateId("TEMP-123")
            .build();
    }


    private RefundNotificationLetterRequest getRefundNotificationLetterRequest() {
        return RefundNotificationLetterRequest.refundNotificationLetterRequestWith()
            .notificationType(NotificationType.LETTER)
            .reference("RF-1234-1234-1234")
            .personalisation(
                    Personalisation.personalisationRequestWith()
                        .serviceUrl("service@gmail.com")
                        .serviceMailBox("service")
                        .refundReference("RF-1234-1234-1234")
                        .build())
            .recipientPostalAddress(RecipientPostalAddress.recipientPostalAddressWith()
                                        .addressLine("PB 123")
                                        .city("Nottingham")
                                        .postalCode("NG2 2DA")
                                        .build())
            .templateId("TEMP-123")
            .build();
    }
}
