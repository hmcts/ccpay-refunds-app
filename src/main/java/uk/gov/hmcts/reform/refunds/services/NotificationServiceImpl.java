package uk.gov.hmcts.reform.refunds.services;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationEmailRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationLetterRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.TemplatePreview;
import uk.gov.hmcts.reform.refunds.dtos.responses.Notification;
import uk.gov.hmcts.reform.refunds.dtos.responses.NotificationsDtoResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentGroupResponse;
import uk.gov.hmcts.reform.refunds.dtos.responses.PaymentResponse;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundNotificationResendRequestException;
import uk.gov.hmcts.reform.refunds.mapper.RefundNotificationMapper;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.utils.RefundsUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType.EMAIL;
import static uk.gov.hmcts.reform.refunds.dtos.enums.NotificationType.LETTER;

@Service
public class NotificationServiceImpl implements NotificationService {

    @Autowired
    private RestTemplate restTemplateNotify;

    @Autowired
    private RefundNotificationMapper refundNotificationMapper;

    @Autowired
    RefundsUtil refundsUtil;

    @Value("${notification.url}")
    private String notificationUrl;

    @Value("${notification.email-path}")
    private String emailUrlPath;

    @Value("${notification.letter-path}")
    private String letterUrlPath;

    public static final String CONTENT_TYPE = "content-type";

    private static final String NOTIFICATION_NOT_SENT = "Notification not sent";

    @Autowired
    private AuthTokenGenerator authTokenGenerator;

    @Autowired
    private PaymentService paymentService;

    private static Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private static final Pattern EXCEPTIONPATTERN = Pattern.compile("\\[(.*?)\\]");

    @Override
    public ResponseEntity<String> postEmailNotificationData(MultiValueMap<String, String> headers,
                                                            RefundNotificationEmailRequest refundNotificationEmailRequest) {
        log.info("RefundNotificationEmailRequest object: {}", refundNotificationEmailRequest);

        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(new StringBuilder(notificationUrl).append(emailUrlPath).toString());
            log.info("Notification URL in Refunds app email {}",builder.toUriString());
            return restTemplateNotify.exchange(builder.toUriString(), HttpMethod.POST,
                    new HttpEntity<>(refundNotificationEmailRequest, getFormatedHeaders(headers)),String.class);
        } catch (HttpClientErrorException exception) {
            handleHttpClientErrorException(exception);
        } catch (HttpServerErrorException exception) {
            log.error("Exception message {}",exception.getMessage());
            log.error("Notification service is unavailable. Please try again later. {}", exception);
        }
        return new ResponseEntity<>("Notification service is unavailable",HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Override
    public ResponseEntity<String> postLetterNotificationData(MultiValueMap<String, String> headers,
                                                             RefundNotificationLetterRequest refundNotificationLetterRequest) {

        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(new StringBuilder(notificationUrl).append(letterUrlPath).toString());
            log.info("Notification URL in Refunds app for letter {}",builder.toUriString());
            return restTemplateNotify.exchange(builder.toUriString(), HttpMethod.POST,new HttpEntity<>(refundNotificationLetterRequest,
                    getFormatedHeaders(headers)),String.class);
        } catch (HttpClientErrorException exception) {
            handleHttpClientErrorException(exception);
        } catch (HttpServerErrorException exception) {
            log.info("Notification service is unavailable. Please try again later.");
        }
        return new ResponseEntity<>("Notification service is unavailable",HttpStatus.SERVICE_UNAVAILABLE);

    }


    private MultiValueMap<String, String> getFormatedHeaders(MultiValueMap<String, String> headers) {
        List<String> authtoken = headers.get("authorization");
        List<String> servauthtoken = Arrays.asList(authTokenGenerator.generate());
        MultiValueMap<String, String> inputHeaders = new LinkedMultiValueMap<>();
        inputHeaders.put(CONTENT_TYPE, headers.get(CONTENT_TYPE));
        inputHeaders.put("Authorization", authtoken);
        inputHeaders.put("ServiceAuthorization", servauthtoken);
        return inputHeaders;
    }

    private void handleHttpClientErrorException(HttpClientErrorException exception) {
        String exceptionMessage = "Invalid Refund notification request.";
        HttpStatusCode status = exception.getStatusCode();
        log.info("Notifications Response code {}", status);
        log.info("Notifications Response message {}", exception.getMessage());

        if (status.equals(HttpStatus.BAD_REQUEST) || status.equals(HttpStatus.FORBIDDEN)
                || status.equals(HttpStatus.TOO_MANY_REQUESTS) || status.equals(HttpStatus.UNPROCESSABLE_ENTITY)) {
            Matcher matcher = EXCEPTIONPATTERN.matcher(exception.getMessage());
            if (matcher.find()) {
                exceptionMessage = matcher.group(1);
            } else {
                exceptionMessage = exception.getMessage();
            }
        }
        throw new InvalidRefundNotificationResendRequestException(exceptionMessage, exception);
    }

    @Override
    public void updateNotification(MultiValueMap<String, String> headers, Refund refund, TemplatePreview templatePreview) {
        updateNotification(headers, refund, templatePreview, null);
    }

    @Override
    public void updateNotification(MultiValueMap<String, String> headers, Refund refund,
                                   TemplatePreview templatePreview, String templateId) {

        ResponseEntity<String>  responseEntity =  sendNotification(headers, refund, templatePreview, templateId);
        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            refund.setNotificationSentFlag("SENT");
            refund.setContactDetails(null);
        } else if (responseEntity.getStatusCode().is5xxServerError()) {
            if (refund.getContactDetails().getNotificationType().equals(EMAIL.name())) {
                refund.setNotificationSentFlag("EMAIL_NOT_SENT");
                log.error(NOTIFICATION_NOT_SENT);
            } else {
                refund.setNotificationSentFlag("LETTER_NOT_SENT");
                log.error(NOTIFICATION_NOT_SENT);
            }
        } else {
            refund.setNotificationSentFlag("ERROR");
            log.error(NOTIFICATION_NOT_SENT);
        }
    }

    private ResponseEntity<String> sendNotification(
        MultiValueMap<String, String> headers, Refund refund, TemplatePreview templatePreview, String notificationTemplateId) {

        ResponseEntity<String> responseEntity;

        String templateId = StringUtils.isEmpty(notificationTemplateId) ? refundsUtil.getTemplate(refund) : notificationTemplateId;

        log.info("Send notification template id final {}", templateId);

        String customerReference = retrieveCustomerReference(headers, refund.getPaymentReference());

        if (EMAIL.name().equals(refund.getContactDetails().getNotificationType())) {
            ContactDetails newContact = ContactDetails.contactDetailsWith()
                .email(refund.getContactDetails().getEmail())
                .templateId(templateId)
                .notificationType(EMAIL.name())
                .build();
            refund.setContactDetails(newContact);
            refund.setNotificationSentFlag("email_not_sent");
            RefundNotificationEmailRequest refundNotificationEmailRequest = refundNotificationMapper
                .getRefundNotificationEmailRequestApproveJourney(refund, templatePreview, templateId, customerReference);
            log.info("send notification  -> " + refundNotificationEmailRequest);
            responseEntity = postEmailNotificationData(headers,refundNotificationEmailRequest);
        } else {
            ContactDetails newContact = ContactDetails.contactDetailsWith()
                .templateId(templateId)
                .addressLine(refund.getContactDetails().getAddressLine())
                .county(refund.getContactDetails().getCounty())
                .postalCode(refund.getContactDetails().getPostalCode())
                .city(refund.getContactDetails().getCity())
                .country(refund.getContactDetails().getCountry())
                .notificationType(LETTER.name())
                .build();
            refund.setContactDetails(newContact);
            refund.setNotificationSentFlag("letter_not_sent");
            RefundNotificationLetterRequest refundNotificationLetterRequestRequest = refundNotificationMapper
                .getRefundNotificationLetterRequestApproveJourney(refund, templatePreview, templateId, customerReference);
            responseEntity = postLetterNotificationData(headers,refundNotificationLetterRequestRequest);

        }
        return responseEntity;
    }

    @Override
    public Notification getNotificationDetails(MultiValueMap<String, String> headers, String reference) {
        Notification notificationDetails = null;

        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(
                new StringBuilder(notificationUrl).append("/notifications/").append(reference)
                    .toString());

            log.info("Notification URL in Refunds app for get notification details {}", builder.toUriString());
            ResponseEntity<NotificationsDtoResponse> responseNotification = restTemplateNotify
                .exchange(
                    builder.toUriString(),
                    HttpMethod.GET,
                    new HttpEntity<>(getFormatedHeaders(headers)), NotificationsDtoResponse.class);

            if (responseNotification != null) {

                NotificationsDtoResponse notificationsDtoResponse = responseNotification.getBody();
                /*
                    Getting last notification type which was used while approval or send last notification.
                */
                if (null != notificationsDtoResponse) {
                    notificationDetails = notificationsDtoResponse.getNotifications().get(0);
                }
            }

        } catch (HttpClientErrorException exception) {
            log.error("Last exception e {}", exception.getMessage());
            log.error("Last exception {}", exception);
        } catch (HttpServerErrorException exception) {
            log.error("Exception message {}",exception.getMessage());
            log.error("Notification service is unavailable. Please try again later. {}", exception);
        } catch (Exception e) {
            log.error("Last exception e {}", e.getMessage());
            log.error("Last exception {}", e);
        }
        return notificationDetails;
    }

    public String retrieveCustomerReference(MultiValueMap<String, String> headers, String paymentReference) {
        String customerReference = "";
        List<String> paymentReferenceList = new ArrayList<>();
        paymentReferenceList.add(paymentReference);

        PaymentGroupResponse paymentData = paymentService.fetchPaymentGroupResponse(headers, paymentReference);

        // Loop through the payment responses to get the customer reference
        for (PaymentResponse paymentDtoResponse : paymentData.getPayments()) {
            if (paymentDtoResponse.getCustomerReference() != null) {
                customerReference = paymentDtoResponse.getCustomerReference();
                break;
            }
        }
        return customerReference;
    }

}
