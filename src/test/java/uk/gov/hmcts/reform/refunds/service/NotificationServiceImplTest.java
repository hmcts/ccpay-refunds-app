package uk.gov.hmcts.reform.refunds.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
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
import uk.gov.hmcts.reform.refunds.dtos.requests.FromTemplateContact;
import uk.gov.hmcts.reform.refunds.dtos.requests.MailAddress;
import uk.gov.hmcts.reform.refunds.dtos.requests.Personalisation;
import uk.gov.hmcts.reform.refunds.dtos.requests.RecipientPostalAddress;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationEmailRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationLetterRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.TemplatePreview;
import uk.gov.hmcts.reform.refunds.dtos.responses.CurrencyCode;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentAllocationResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentFeeResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentGroupResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RemissionResponse;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundNotificationResendRequestException;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.services.NotificationService;
import uk.gov.hmcts.reform.refunds.services.NotificationServiceImpl;
import uk.gov.hmcts.reform.refunds.services.PaymentService;
import uk.gov.hmcts.reform.refunds.utils.RefundsUtil;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK;
import static org.testng.Assert.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = MOCK)
@ActiveProfiles({"local", "test"})
class NotificationServiceImplTest {

    @Autowired
    private NotificationService notificationService;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private NotificationServiceImpl notificationServiceImpl;

    @MockBean
    private AuthTokenGenerator authTokenGenerator;

    @MockBean
    private RefundsRepository refundsRepository;

    @MockBean
    @Qualifier("restTemplateNotify")
    private RestTemplate restTemplateNotify;

    @MockBean
    @Qualifier("restTemplatePayment")
    private RestTemplate restTemplatePayment;

    @BeforeEach
    public void setUp() {
     //   MockitoAnnotations.openMocks(this);
    }

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

    private Refund getRefundForLetter() {
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
                                .addressLine("Test addressline")
                                .county("county")
                                .country("country")
                                .city("test")
                                .postalCode("TA1 TA2")
                                .notificationType("LETTER")
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

    private TemplatePreview getTemplatePreviewForEmail() {
        return TemplatePreview.templatePreviewWith()
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
    }

    private TemplatePreview getTemplatePreviewForLetter() {
        return TemplatePreview.templatePreviewWith()
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

        when(restTemplatePayment.exchange(anyString(), Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(getPaymentGroupDto())

        ));

        when(refundsRepository.save(any(Refund.class))).thenReturn(refund);

        notificationService.updateNotification(getHeaders(),refund,  null,"template-1");

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

        when(restTemplatePayment.exchange(anyString(), Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(getPaymentGroupDto())

        ));

        when(refundsRepository.save(any(Refund.class))).thenReturn(refund);

        notificationService.updateNotification(getHeaders(), refund, null,"template-1");

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

        when(restTemplatePayment.exchange(anyString(), Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(getPaymentGroupDto())

        ));

        when(refundsRepository.save(any(Refund.class))).thenReturn(refund);

        notificationService.updateNotification(getHeaders(), refund, null,"template-1");

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

        when(restTemplatePayment.exchange(anyString(), Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(getPaymentGroupDto())

        ));

        when(refundsRepository.save(any(Refund.class))).thenReturn(refund);

        notificationService.updateNotification(getHeaders(), refund, null,"template-1");

        assertEquals("ERROR",refund.getNotificationSentFlag());
    }

    @Test
    void updateNotificationSuccessWithTemplatePreviewAndEmail() {

        Refund refund = getRefund();
        refund.setRefundStatus(RefundStatus.REJECTED);
        refund.setReason(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON);
        when(restTemplateNotify.exchange(anyString(),
                                         Mockito.any(HttpMethod.class),
                                         Mockito.any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("Success", HttpStatus.OK));

        when(restTemplatePayment.exchange(anyString(), Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(getPaymentGroupDto())

        ));

        when(refundsRepository.save(any(Refund.class))).thenReturn(refund);

        notificationService.updateNotification(getHeaders(),refund, getTemplatePreviewForEmail());

        assertEquals("SENT",refund.getNotificationSentFlag());
    }

    @Test
    void updateNotificationSuccessWithTemplatePreviewAndLetter() {

        Refund refund = getRefundForLetter();
        refund.setRefundStatus(RefundStatus.REJECTED);
        refund.setReason(RefundsUtil.REFUND_WHEN_CONTACTED_REJECT_REASON);
        when(restTemplateNotify.exchange(anyString(),
                                         Mockito.any(HttpMethod.class),
                                         Mockito.any(HttpEntity.class), eq(String.class)))
            .thenReturn(new ResponseEntity<>("Success", HttpStatus.OK));

        when(restTemplatePayment.exchange(anyString(), Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(getPaymentGroupDto())

        ));

        when(refundsRepository.save(any(Refund.class))).thenReturn(refund);

        notificationService.updateNotification(getHeaders(),refund, getTemplatePreviewForLetter());

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

        when(restTemplatePayment.exchange(anyString(), Mockito.any(HttpMethod.class), Mockito.any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(getPaymentGroupDto())

        ));

        when(refundsRepository.save(any(Refund.class))).thenReturn(refund);

        notificationService.updateNotification(getHeaders(),refund, null, null);

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



    private PaymentGroupResponse getPaymentGroupDto() {
        return PaymentGroupResponse.paymentGroupDtoWith()
            .paymentGroupReference("payment-group-reference")
            .dateCreated(Date.from(Instant.now()))
            .dateUpdated(Date.from(Instant.now()))
            .payments(Collections.singletonList(
                PaymentResponse.paymentResponseWith()
                    .amount(BigDecimal.valueOf(100))
                    .description("description")
                    .reference("RC-1628-5241-9956-2315")
                    .customerReference("ABCDE/123456")
                    .dateCreated(Date.from(Instant.now()))
                    .dateUpdated(Date.from(Instant.now()))
                    .currency(CurrencyCode.GBP)
                    .caseReference("case-reference")
                    .ccdCaseNumber("ccd-case-number")
                    .channel("solicitors portal")
                    .method("payment by account")
                    .externalProvider("provider")
                    .accountNumber("PBAFUNC1234")
                    .paymentAllocation(Collections.singletonList(
                        PaymentAllocationResponse.paymentAllocationDtoWith()
                            .allocationStatus("allocationStatus")
                            .build()
                    ))
                    .build()
            ))
            .remissions(Collections.singletonList(
                RemissionResponse.remissionDtoWith()
                    .remissionReference("remission-reference")
                    .beneficiaryName("ben-ten")
                    .ccdCaseNumber("ccd-case-number")
                    .caseReference("case-reference")
                    .hwfReference("hwf-reference")
                    .hwfAmount(BigDecimal.valueOf(100))
                    .dateCreated(Date.from(Instant.now()))
                    .feeId(50)
                    .build()
            ))
            .fees(Collections.singletonList(
                PaymentFeeResponse.feeDtoWith()
                    .id(50)
                    .code("FEE012")
                    .feeAmount(BigDecimal.valueOf(100))
                    .calculatedAmount(BigDecimal.valueOf(100))
                    .netAmount(BigDecimal.valueOf(100))
                    .version("1")
                    .volume(1)
                    .feeAmount(BigDecimal.valueOf(100))
                    .ccdCaseNumber("ccd-case-number")
                    .reference("reference")
                    .memoLine("memo-line")
                    .naturalAccountCode("natural-account-code")
                    .description("description")
                    .allocatedAmount(BigDecimal.valueOf(100))
                    .apportionAmount(BigDecimal.valueOf(100))
                    .dateCreated(Date.from(Instant.now()))
                    .dateUpdated(Date.from(Instant.now()))
                    .dateApportioned(Date.from(Instant.now()))
                    .amountDue(BigDecimal.valueOf(0))
                    .build()
            )).build();
    }
}
