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
import uk.gov.hmcts.reform.refunds.config.toggler.LaunchDarklyFeatureToggler;
import uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType;
import uk.gov.hmcts.reform.refunds.dtos.requests.RecipientPostalAddress;
import uk.gov.hmcts.reform.refunds.dtos.requests.ResendNotificationRequest;
import uk.gov.hmcts.reform.refunds.dtos.responses.CurrencyCode;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamTokenResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.IdamUserIdResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentAllocationResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentFeeResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentGroupResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.ReconciliationProviderResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.RemissionResponse;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundNotificationResendRequestException;
import uk.gov.hmcts.reform.refunds.mapper.RefundNotificationMapper;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.model.RefundReason;
import uk.gov.hmcts.reform.refunds.model.RefundStatus;
import uk.gov.hmcts.reform.refunds.model.StatusHistory;
import uk.gov.hmcts.reform.refunds.repository.RefundReasonRepository;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.services.IdamServiceImpl;
import uk.gov.hmcts.reform.refunds.services.NotificationServiceImpl;
import uk.gov.hmcts.reform.refunds.services.PaymentService;
import uk.gov.hmcts.reform.refunds.services.RefundNotificationService;
import uk.gov.hmcts.reform.refunds.services.RefundsService;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
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

    @MockBean
    private PaymentService paymentService;


    @MockBean
    private NotificationServiceImpl notificationService;

    @MockBean
    private IdamServiceImpl idamService;

    @MockBean
    @Qualifier("restTemplateLiberata")
    private RestTemplate restTemplateLiberata;

    @MockBean
    @Qualifier("restTemplateIdam")
    private RestTemplate restTemplateIdam;

    @MockBean
    private LaunchDarklyFeatureToggler featureToggler;

    @MockBean
    private RefundReasonRepository refundReasonRepository;

    @MockBean
    @Qualifier("restTemplatePayment")
    private RestTemplate restTemplatePayment;

    private IdamUserIdResponse mockIdamUserIdResponse = IdamUserIdResponse.idamUserIdResponseWith()
        .familyName("VP")
        .givenName("VP")
        .name("VP")
        .sub("V_P@gmail.com")
        .roles(Collections.singletonList("vp"))
        .uid("986-erfg-kjhg-123")
        .build();

    @Test
    void resendEmailRefundNotificationShouldReturnSuccessResponse_AfterSuccessfulRestcallWithNotificationService() {
        ResendNotificationRequest mockRequest = getMockEmailRequest();
        when(refundsService.getRefundForReference(anyString())).thenReturn(getMockRefund());
        PaymentGroupResponse paymentData = PaymentGroupResponse.paymentGroupDtoWith()
                .payments(Arrays.asList(
                        PaymentResponse.paymentResponseWith().reference("RF-1233-2134-1234-1234").method("cash").channel("bulk scan").build()))
                .build();
        when(paymentService.fetchPaymentGroupResponse(any(), anyString())).thenReturn(paymentData);
        when(restTemplateNotify.exchange(anyString(),any(HttpMethod.class),any(HttpEntity.class),eq(String.class))).thenReturn(
            new ResponseEntity<String>("Success", HttpStatus.OK)
        );
        when(authTokenGenerator.generate()).thenReturn("Service.Auth.Token");
        when(refundsRepository.save(any(Refund.class))).thenReturn(getMockRefund());
        when(notificationService.postEmailNotificationData(any(), any())).thenReturn(new ResponseEntity<>(HttpStatus.OK));
        when(notificationService.postLetterNotificationData(any(), any())).thenReturn(new ResponseEntity<>(HttpStatus.OK));
        ResponseEntity<String> responseEntity = refundNotificationService.resendRefundNotification(mockRequest,getHeaders());
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
    }

    @Test
    void resendLetterRefundNotificationShouldReturnSuccessResponse_AfterSuccessfulRestcallWithNotificationService() {
        ResendNotificationRequest mockRequest = getMockLetterRequest();
        when(refundsService.getRefundForReference(anyString())).thenReturn(getMockRefund());
        PaymentGroupResponse paymentData = PaymentGroupResponse.paymentGroupDtoWith()
                .payments(Arrays.asList(
                        PaymentResponse.paymentResponseWith().reference("RF-1233-2134-1234-1234").method("cash").channel("bulk scan").build()))
                .build();
        when(paymentService.fetchPaymentGroupResponse(any(), anyString())).thenReturn(paymentData);
        when(restTemplateNotify.exchange(anyString(),any(HttpMethod.class),any(HttpEntity.class),eq(String.class))).thenReturn(
            new ResponseEntity<String>("Success", HttpStatus.OK)
        );
        when(authTokenGenerator.generate()).thenReturn("Service.Auth.Token");
        when(refundsRepository.save(any(Refund.class))).thenReturn(getMockRefund());
        when(notificationService.postEmailNotificationData(any(), any())).thenReturn(new ResponseEntity<>(HttpStatus.OK));
        when(notificationService.postLetterNotificationData(any(), any())).thenReturn(new ResponseEntity<>(HttpStatus.OK));
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

    @Test
    void processFailedNotificationsEmailTest() throws Exception {

        IdamTokenResponse tokenres =  IdamTokenResponse
            .idamFullNameRetrivalResponseWith()
            .accessToken("test token")
            .refreshToken("mock token")
            .scope("mock-scope")
            .idToken("mock-token")
            .tokenType("mock-type")
            .expiresIn("2021-07-20T11:03:08.067Z")
            .build();

        ResponseEntity<IdamTokenResponse> idamTokenResponse = null;

        when(refundsRepository.findByNotificationSentFlag(anyString())).thenReturn(Optional.ofNullable(List.of(
            RefundServiceImplTest.refundListContactDetailsEmail.get())));

        when(notificationService.postEmailNotificationData(any(), any())).thenReturn(new ResponseEntity<>(HttpStatus.OK));
        when(notificationService.postLetterNotificationData(any(), any())).thenReturn(new ResponseEntity<>(HttpStatus.OK));
        when(authTokenGenerator.generate()).thenReturn("service auth token");
        when(idamService.getSecurityTokens()).thenReturn(tokenres);
        when(refundsRepository.save(any(Refund.class))).thenReturn(getRefund());

        refundNotificationService.processFailedNotificationsEmail();

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


    @Test
    void processFailedNotificationsLetterTest() throws Exception {

        IdamTokenResponse tokenres =  IdamTokenResponse
            .idamFullNameRetrivalResponseWith()
            .accessToken("test token")
            .refreshToken("mock token")
            .scope("mock-scope")
            .idToken("mock-token")
            .tokenType("mock-type")
            .expiresIn("2021-07-20T11:03:08.067Z")
            .build();

        ResponseEntity<IdamTokenResponse> idamTokenResponse = null;

        when(refundsRepository.findByNotificationSentFlag(anyString())).thenReturn(Optional.ofNullable(List.of(
            RefundServiceImplTest.refundListContactDetailsLetter.get())));

        when(notificationService.postEmailNotificationData(any(), any())).thenReturn(new ResponseEntity<>(HttpStatus.OK));
        when(notificationService.postLetterNotificationData(any(), any())).thenReturn(new ResponseEntity<>(HttpStatus.OK));
        when(authTokenGenerator.generate()).thenReturn("service auth token");
        when(idamService.getSecurityTokens()).thenReturn(tokenres);
        when(refundsRepository.save(any(Refund.class))).thenReturn(getRefund());
        refundNotificationService.processFailedNotificationsLetter();
    }

    @Test
    void processFailedLiberataRefundsApproveJourneyTest() throws Exception {

        IdamTokenResponse tokenres =  IdamTokenResponse
            .idamFullNameRetrivalResponseWith()
            .accessToken("test token")
            .refreshToken("mock token")
            .scope("mock-scope")
            .idToken("mock-token")
            .tokenType("mock-type")
            .expiresIn("2021-07-20T11:03:08.067Z")
            .build();

        ResponseEntity<IdamTokenResponse> idamTokenResponse = null;

        when(authTokenGenerator.generate()).thenReturn("service auth token");

        when(idamService.getSecurityTokens()).thenReturn(tokenres);

        ResponseEntity<IdamUserIdResponse> responseEntity = new ResponseEntity<>(mockIdamUserIdResponse, HttpStatus.OK);

        when(restTemplateIdam.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class),
                                       eq(IdamUserIdResponse.class)
        )).thenReturn(responseEntity);

        when(refundsRepository.findByRefundStatusAndRefundApproveFlag(anyString(),anyString())).thenReturn(Optional.ofNullable(List.of(
            RefundServiceImplTest.refundListLiberataRefundsTest.get())));

        when(featureToggler.getBooleanValue(eq("refund-liberata"), anyBoolean())).thenReturn(true);

        when(restTemplatePayment.exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            PaymentGroupResponse.class))).thenReturn(ResponseEntity.of(
            Optional.of(getPaymentGroupDto())

        ));

        when(refundReasonRepository.findByCode(anyString())).thenReturn(Optional.of(RefundReason.refundReasonWith().name(
            "refund reason").build()));

        doReturn(ResponseEntity.ok(Optional.of(ReconciliationProviderResponse.buildReconciliationProviderResponseWith()
                                                   .amount(BigDecimal.valueOf(100))
                                                   .refundReference("RF-1628-5241-9956-2215")
                                                   .build()
        ))).when(restTemplateLiberata).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), eq(
            ReconciliationProviderResponse.class));

        when(refundsRepository.save(any(Refund.class))).thenReturn(getRefund());
        refundNotificationService.reprocessPostFailedRefundsToLiberata();

    }

    private PaymentGroupResponse getPaymentGroupDto() {
        return PaymentGroupResponse.paymentGroupDtoWith()
            .paymentGroupReference("payment-group-reference")
            .dateCreated(Date.from(Instant.now()))
            .dateUpdated(Date.from(Instant.now()))
            .payments(Arrays.asList(
                PaymentResponse.paymentResponseWith()
                    .amount(BigDecimal.valueOf(100))
                    .description("description")
                    .reference("RC-1628-5241-9956-2315")
                    .dateCreated(Date.from(Instant.now()))
                    .dateUpdated(Date.from(Instant.now()))
                    .currency(CurrencyCode.GBP)
                    .caseReference("case-reference")
                    .ccdCaseNumber("ccd-case-number")
                    .channel("solicitors portal")
                    .method("payment by account")
                    .externalProvider("provider")
                    .accountNumber("PBAFUNC1234")
                    .paymentAllocation(Arrays.asList(
                        PaymentAllocationResponse.paymentAllocationDtoWith()
                            .allocationStatus("allocationStatus")
                            .build()
                    ))
                    .build()
            ))
            .remissions(Arrays.asList(
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
            .fees(Arrays.asList(
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
