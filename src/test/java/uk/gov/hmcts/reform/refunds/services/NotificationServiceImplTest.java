package uk.gov.hmcts.reform.refunds.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType;
import uk.gov.hmcts.reform.refunds.dtos.requests.DocPreviewRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.Personalisation;
import uk.gov.hmcts.reform.refunds.dtos.requests.RecipientPostalAddress;
import uk.gov.hmcts.reform.refunds.dtos.responses.NotificationTemplatePreviewResponse;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundNotificationResendRequestException;
import uk.gov.hmcts.reform.refunds.mapper.RefundNotificationMapper;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.utils.RefundsUtil;
import uk.gov.hmcts.reform.refunds.utils.StatusHistoryUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationServiceImplTest {

    @Mock
    private RestTemplate restTemplateNotify;
    @Mock
    private RefundNotificationMapper refundNotificationMapper; // not used in these tests
    @Mock
    private RefundsUtil refundsUtil;
    @Mock
    private AuthTokenGenerator authTokenGenerator;
    @Mock
    private PaymentService paymentService; // not used here
    @Mock
    private StatusHistoryUtil statusHistoryUtil;
    @Mock
    private RefundsRepository refundsRepository;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        // inject configuration values
        TestUtils.setField(notificationService, "notificationUrl", "http://notify.local");
        TestUtils.setField(notificationService, "emailUrlPath", "/notifications/email");
        TestUtils.setField(notificationService, "letterUrlPath", "/notifications/letter");
    }

    private DocPreviewRequest buildDocPreviewRequest(String templateId) {
        return DocPreviewRequest.docPreviewRequestWith()
            .paymentReference("RF-1746-5507-4452-0488")
            .paymentMethod("card")
            .paymentChannel("online")
            .serviceName("cmc")
            .recipientEmailAddress("user@example.com")
            .notificationType(NotificationType.EMAIL)
            .templateId(templateId)
            .personalisation(Personalisation.personalisationRequestWith()
                .ccdCaseNumber("1111222233334444")
                .refundReference("RF-1746-5507-4452-0488")
                .customerReference("RC-1234-5678-9012-3456")
                .build())
            .build();
    }

    private MultiValueMap<String, String> buildHeaders() {
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();
        headers.add("content-type", "application/json");
        headers.add("authorization", "Bearer user-token");
        when(authTokenGenerator.generate()).thenReturn("service-token");
        return headers;
    }

    private Refund buildRefund() {
        return Refund.refundsWith()
            .reference("RF-1746-5507-4452-0488")
            .reason("RR001")
            .paymentReference("RC-1234-5678-9012-3456")
            .build();
    }

    @Test
    void previewNotification_setsTemplateIdFromRefundsUtilAndCallsNotify() {
        DocPreviewRequest request = buildDocPreviewRequest(null); // missing templateId
        MultiValueMap<String, String> headers = buildHeaders();
        Refund refund = buildRefund();
        when(refundsRepository.findByReferenceOrThrow(eq("RF-1746-5507-4452-0488"))).thenReturn(refund);
        when(statusHistoryUtil.isAClonedRefund(refund)).thenReturn(false);
        when(statusHistoryUtil.getOriginalNoteForRejected(refund)).thenReturn(null);
        when(refundsUtil.getTemplate(eq(refund), eq("RR001"))).thenReturn("template-123");

        NotificationTemplatePreviewResponse body = NotificationTemplatePreviewResponse
            .buildNotificationTemplatePreviewWith()
            .templateId("template-123")
            .templateType("email")
            .subject("subject")
            .body("body")
            .build();
        ResponseEntity<NotificationTemplatePreviewResponse> okResponse = ResponseEntity.ok(body);

        when(restTemplateNotify.exchange(eq("http://notify.local/notifications/doc-preview"),
            eq(HttpMethod.POST), any(HttpEntity.class), eq(NotificationTemplatePreviewResponse.class)))
            .thenReturn(okResponse);

        NotificationTemplatePreviewResponse response = notificationService.previewNotification(request, headers);
        assertNotNull(response);
        assertEquals("template-123", response.getTemplateId());

        // verify template was set on request before sending
        assertEquals("template-123", request.getTemplateId());

        // capture headers sent to notify service
        ArgumentCaptor<HttpEntity<?>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplateNotify).exchange(eq("http://notify.local/notifications/doc-preview"), eq(HttpMethod.POST),
            entityCaptor.capture(), eq(NotificationTemplatePreviewResponse.class));
        HttpEntity<?> entity = entityCaptor.getValue();
        var sentHeaders = entity.getHeaders();
        assertEquals("application/json", sentHeaders.getFirst("content-type"));
        assertEquals("Bearer user-token", sentHeaders.getFirst("Authorization"));
        assertEquals("service-token", sentHeaders.getFirst("ServiceAuthorization"));
    }

    @Test
    void previewNotification_usesProvidedTemplateId_doesNotCallGetTemplate() {
        DocPreviewRequest request = buildDocPreviewRequest("provided-template");
        MultiValueMap<String, String> headers = buildHeaders();
        Refund refund = buildRefund();
        when(refundsRepository.findByReferenceOrThrow(eq("RF-1746-5507-4452-0488"))).thenReturn(refund);
        when(statusHistoryUtil.isAClonedRefund(refund)).thenReturn(false);
        when(statusHistoryUtil.getOriginalNoteForRejected(refund)).thenReturn(null);

        NotificationTemplatePreviewResponse body = NotificationTemplatePreviewResponse
            .buildNotificationTemplatePreviewWith()
            .templateId("provided-template")
            .templateType("email")
            .subject("subject")
            .body("body")
            .build();
        ResponseEntity<NotificationTemplatePreviewResponse> okResponse = ResponseEntity.ok(body);

        when(restTemplateNotify.exchange(eq("http://notify.local/notifications/doc-preview"),
            eq(HttpMethod.POST), any(HttpEntity.class), eq(NotificationTemplatePreviewResponse.class)))
            .thenReturn(okResponse);

        NotificationTemplatePreviewResponse response = notificationService.previewNotification(request, headers);
        assertNotNull(response);
        assertEquals("provided-template", response.getTemplateId());

        verify(refundsUtil, never()).getTemplate(any(Refund.class), anyString());
    }

    @Test
    void previewNotification_clientError_throwsInvalidRefundNotificationResendRequestException() {
        Refund refund = buildRefund();
        when(refundsRepository.findByReferenceOrThrow(eq("RF-1746-5507-4452-0488"))).thenReturn(refund);
        when(statusHistoryUtil.isAClonedRefund(refund)).thenReturn(false);
        when(statusHistoryUtil.getOriginalNoteForRejected(refund)).thenReturn(null);
        when(refundsUtil.getTemplate(eq(refund), eq("RR001"))).thenReturn("template-123");

        String url = UriComponentsBuilder.fromUriString("http://notify.local/notifications/doc-preview").toUriString();
        when(restTemplateNotify.exchange(eq(url), eq(HttpMethod.POST), any(HttpEntity.class), eq(NotificationTemplatePreviewResponse.class)))
            .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "Bad Request [Invalid request]"));

        DocPreviewRequest request = buildDocPreviewRequest(null);
        MultiValueMap<String, String> headers = buildHeaders();
        assertThrows(InvalidRefundNotificationResendRequestException.class,
            () -> notificationService.previewNotification(request, headers));
    }

    @Test
    void previewNotification_serverError_returnsNull() {
        DocPreviewRequest request = buildDocPreviewRequest(null);
        MultiValueMap<String, String> headers = buildHeaders();
        Refund refund = buildRefund();
        when(refundsRepository.findByReferenceOrThrow(eq("RF-1746-5507-4452-0488"))).thenReturn(refund);
        when(statusHistoryUtil.isAClonedRefund(refund)).thenReturn(false);
        when(statusHistoryUtil.getOriginalNoteForRejected(refund)).thenReturn(null);
        when(refundsUtil.getTemplate(eq(refund), eq("RR001"))).thenReturn("template-123");

        String url = UriComponentsBuilder.fromUriString("http://notify.local/notifications/doc-preview").toUriString();
        HttpServerErrorException serverError =
            new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE, "Notify unavailable");
        when(restTemplateNotify.exchange(eq(url), eq(HttpMethod.POST), any(HttpEntity.class), eq(NotificationTemplatePreviewResponse.class)))
            .thenThrow(serverError);

        NotificationTemplatePreviewResponse response = notificationService.previewNotification(request, headers);
        assertNull(response);
    }

    @Test
    void previewNotification_setsPostalAddressOnRefund_whenLetter() {
        // Arrange: build a LETTER request with postal address
        DocPreviewRequest request = DocPreviewRequest.docPreviewRequestWith()
            .paymentReference("RF-1746-5507-4452-0488")
            .paymentMethod("postal order")
            .paymentChannel("bulk scan")
            .serviceName("cmc")
            .notificationType(NotificationType.LETTER)
            .recipientPostalAddress(RecipientPostalAddress.recipientPostalAddressWith()
                .addressLine("10 Downing Street")
                .city("London")
                .county("Greater London")
                .country("UK")
                .postalCode("SW1A 2AA")
                .build())
            .personalisation(Personalisation.personalisationRequestWith()
                .ccdCaseNumber("1111222233334444")
                .refundReference("RF-1746-5507-4452-0488")
                .customerReference("RC-1234-5678-9012-3456")
                .build())
            .build();

        MultiValueMap<String, String> headers = buildHeaders();
        Refund refund = buildRefund();
        when(refundsRepository.findByReferenceOrThrow(eq("RF-1746-5507-4452-0488"))).thenReturn(refund);
        when(statusHistoryUtil.isAClonedRefund(refund)).thenReturn(false);
        when(statusHistoryUtil.getOriginalNoteForRejected(refund)).thenReturn(null);
        when(refundsUtil.getTemplate(eq(refund), eq("RR001"))).thenReturn("template-xyz");

        NotificationTemplatePreviewResponse body = NotificationTemplatePreviewResponse
            .buildNotificationTemplatePreviewWith()
            .templateId("template-xyz")
            .templateType("letter")
            .subject("subject")
            .body("body")
            .build();
        ResponseEntity<NotificationTemplatePreviewResponse> okResponse = ResponseEntity.ok(body);

        String url = UriComponentsBuilder.fromUriString("http://notify.local/notifications/doc-preview").toUriString();
        when(restTemplateNotify.exchange(eq(url), eq(HttpMethod.POST), any(HttpEntity.class), eq(NotificationTemplatePreviewResponse.class)))
            .thenReturn(okResponse);

        // Act
        NotificationTemplatePreviewResponse response = notificationService.previewNotification(request, headers);

        // Assert: refund.contactDetails populated from request
        assertNotNull(response);
        assertNotNull(refund.getContactDetails());
        assertEquals("LETTER", refund.getContactDetails().getNotificationType());
        assertEquals("10 Downing Street", refund.getContactDetails().getAddressLine());
        assertEquals("London", refund.getContactDetails().getCity());
        assertEquals("Greater London", refund.getContactDetails().getCounty());
        assertEquals("UK", refund.getContactDetails().getCountry());
        assertEquals("SW1A 2AA", refund.getContactDetails().getPostalCode());
    }

    // Test-only helper for setting private fields via reflection
    static final class TestUtils {
        static void setField(Object target, String fieldName, Object value) {
            try {
                java.lang.reflect.Field f = target.getClass().getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(target, value);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }
}
