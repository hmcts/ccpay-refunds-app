package uk.gov.hmcts.reform.refunds.service;

import org.junit.jupiter.api.Test;
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
import org.springframework.web.client.RestTemplate;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType;
import uk.gov.hmcts.reform.refunds.dtos.requests.RecipientPostalAddress;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResendNotificationRequest;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundNotificationResendRequestException;
import uk.gov.hmcts.reform.refunds.mapper.RefundNotificationMapper;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.services.RefundNotificationService;
import uk.gov.hmcts.reform.refunds.services.RefundsService;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static uk.gov.hmcts.reform.refunds.service.RefundServiceImplTest.GET_REFUND_LIST_CCD_CASE_NUMBER;
import static uk.gov.hmcts.reform.refunds.service.RefundServiceImplTest.GET_REFUND_LIST_CCD_CASE_USER_ID2;

@SpringBootTest(webEnvironment = MOCK)
@ActiveProfiles({"local", "test"})
class RefundNotificationServiceImplTest {

    @MockBean
    @Qualifier("restTemplateNotify")
    private RestTemplate restTemplateNotify;

    @MockBean
    private RefundNotificationMapper refundNotificationMapper;

    @MockBean
    private RefundsService refundsService;

    @MockBean
    private RefundsRepository refundsRepository;

    @MockBean
    private AuthTokenGenerator authTokenGenerator;

    @Autowired
    RefundNotificationService refundNotificationService;

    @Test
    void resendEmailRefundNotificationShouldReturnSuccessResponse_AfterSuccessfulRestcallWithNotificationService() {
        ResendNotificationRequest mockRequest = getMockEmailRequest();
        when(refundsService.getRefundForReference(anyString())).thenReturn(getMockRefund());
        when(restTemplateNotify.exchange(anyString(),any(HttpMethod.class),any(HttpEntity.class),eq(String.class))).thenReturn(
            new ResponseEntity<String>("Success", HttpStatus.OK)
        );
        when(authTokenGenerator.generate()).thenReturn("Service.Auth.Token");
        when(refundsRepository.save(any(Refund.class))).thenReturn(getMockRefund());
        ResponseEntity<String> responseEntity = refundNotificationService.resendRefundNotification(mockRequest,getHeaders());
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
    }

    @Test
    void resendLetterRefundNotificationShouldReturnSuccessResponse_AfterSuccessfulRestcallWithNotificationService() {
        ResendNotificationRequest mockRequest = getMockLetterRequest();
        when(refundsService.getRefundForReference(anyString())).thenReturn(getMockRefund());
        when(restTemplateNotify.exchange(anyString(),any(HttpMethod.class),any(HttpEntity.class),eq(String.class))).thenReturn(
            new ResponseEntity<String>("Success", HttpStatus.OK)
        );
        when(authTokenGenerator.generate()).thenReturn("Service.Auth.Token");
        when(refundsRepository.save(any(Refund.class))).thenReturn(getMockRefund());
        ResponseEntity<String> responseEntity = refundNotificationService.resendRefundNotification(mockRequest,getHeaders());
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
    }

    @Test
    void resendEmailRefundNotificationShouldReturnBadRequest_WhenNotificationTypeIsEmailAndNoRecipientEmailSent() {
        ResendNotificationRequest mockRequest = getMockEmailRequest();
        mockRequest.setRecipientEmailAddress(null);
        Exception exception = assertThrows(InvalidRefundNotificationResendRequestException.class,
            () -> refundNotificationService.resendRefundNotification(mockRequest, getHeaders()));

        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("Please enter recipient email for Email notification."));

    }

    @Test
    void resendLetterRefundNotificationShouldReturnBadRequest_WhenNotificationTypeIsLetterAndNoPostalAddressSent() {
        ResendNotificationRequest mockRequest = getMockLetterRequest();
        mockRequest.setRecipientPostalAddress(null);

        Exception exception = assertThrows(InvalidRefundNotificationResendRequestException.class,
            () -> refundNotificationService.resendRefundNotification(mockRequest, getHeaders()));

        String actualMessage = exception.getMessage();
        assertTrue(actualMessage.contains("Please enter recipient postal address for Postal notification."));
    }

    private ResendNotificationRequest getMockEmailRequest() {
        return ResendNotificationRequest.resendNotificationRequest()
            .recipientEmailAddress("mock@gmail.com")
            .notificationType(NotificationType.EMAIL)
            .reference("RF-1233-2134-1234-1234")
            .build();
    }

    private ResendNotificationRequest getMockLetterRequest() {
        return ResendNotificationRequest.resendNotificationRequest()
            .recipientPostalAddress(RecipientPostalAddress.recipientPostalAddressWith()
                                        .addressLine("PB 123")
                                        .city("Nottingham")
                                        .postalCode("NG2 2DA")
                                        .build())
            .notificationType(NotificationType.LETTER)
            .reference("RF-1233-2134-1234-1234")
            .build();
    }

    private Refund getMockRefund() {
        return Refund.refundsWith()
            .id(1)
            .amount(BigDecimal.valueOf(100))
            .ccdCaseNumber(GET_REFUND_LIST_CCD_CASE_NUMBER)
            .createdBy(GET_REFUND_LIST_CCD_CASE_USER_ID2)
            .reference("RF-1111-2234-1077-1123")
            .refundStatus(RefundStatus.SENTFORAPPROVAL)
            .reason("RR001")
            .paymentReference("RC-1111-2234-1077-1123")
            .dateCreated(Timestamp.valueOf(LocalDateTime.now()))
            .dateUpdated(Timestamp.valueOf(LocalDateTime.now()))
            .updatedBy(GET_REFUND_LIST_CCD_CASE_USER_ID2)
            .build();
    }

    private MultiValueMap<String,String> getHeaders() {
        MultiValueMap<String, String> inputHeaders = new LinkedMultiValueMap<>();
        inputHeaders.put("Authorization", Arrays.asList("authtoken"));
        inputHeaders.put("content-type", Arrays.asList("application/json"));
        inputHeaders.put("ServiceAuthorization", Arrays.asList("servauthtoken"));
        return inputHeaders;
    }
}
