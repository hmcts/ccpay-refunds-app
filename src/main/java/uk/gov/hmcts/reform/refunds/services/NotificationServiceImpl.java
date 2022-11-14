package uk.gov.hmcts.reform.refunds.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
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
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundNotificationResendRequestException;
import uk.gov.hmcts.reform.refunds.mapper.RefundNotificationMapper;
import uk.gov.hmcts.reform.refunds.model.ContactDetails;
import uk.gov.hmcts.reform.refunds.model.Refund;
import uk.gov.hmcts.reform.refunds.repository.RefundsRepository;
import uk.gov.hmcts.reform.refunds.utils.RefundsUtil;

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
    private RefundsRepository refundsRepository;

    @Autowired
    private NotificationService notificationService;

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

    @Autowired
    private AuthTokenGenerator authTokenGenerator;

    private static Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    private static final Pattern EXCEPTIONPATTERN = Pattern.compile("\\[(.*?)\\]");

    @Override
    public ResponseEntity<String> postEmailNotificationData(MultiValueMap<String, String> headers,
                                                            RefundNotificationEmailRequest refundNotificationEmailRequest) {
        log.info("RefundNotificationEmailRequest object: {}", refundNotificationEmailRequest);

        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(new StringBuilder(notificationUrl).append(emailUrlPath).toString());
            return restTemplateNotify.exchange(builder.toUriString(), HttpMethod.POST,
                    new HttpEntity<>(refundNotificationEmailRequest, getFormatedHeaders(headers)),String.class);
        } catch (HttpClientErrorException exception) {
            handleHttpClientErrorException(exception);
        } catch (HttpServerErrorException exception) {
            log.info("Notification service is unavailable. Please try again later.");
        }
        return new ResponseEntity<>("Notification service is unavailable",HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Override
    public ResponseEntity<String> postLetterNotificationData(MultiValueMap<String, String> headers,
                                                             RefundNotificationLetterRequest refundNotificationLetterRequest) {

        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(new StringBuilder(notificationUrl).append(letterUrlPath).toString());
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
        log.info("ServiceAuthorization: {}", servauthtoken);
        return inputHeaders;
    }

    private void handleHttpClientErrorException(HttpClientErrorException exception) {
        String exceptionMessage = "Invalid Refund notification request.";
        HttpStatus status = exception.getStatusCode();
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
    public void updateNotification(MultiValueMap<String, String> headers, Refund refund) {
        ResponseEntity<String>  responseEntity =  sendNotification(refund, headers);
        if (responseEntity.getStatusCode().is2xxSuccessful()) {
            refund.setNotificationSentFlag("SENT");
            refund.setContactDetails(null);
            refundsRepository.save(refund);
        } else if (responseEntity.getStatusCode().is5xxServerError()) {
            if (refund.getContactDetails().getNotificationType().equals(EMAIL.name())) {
                refund.setNotificationSentFlag("EMAIL_NOT_SENT");
                refundsRepository.save(refund);
                log.error("Notification not sent ");
            } else {
                refund.setNotificationSentFlag("LETTER_NOT_SENT");
                refundsRepository.save(refund);
                log.error("Notification not sent ");
            }
        } else {
            refund.setNotificationSentFlag("ERROR");
            refundsRepository.save(refund);
            log.error("Notification not sent ");
        }
        log.info("updateNotification 1 ---> " + refund.toString());
        log.info("updateNotification 2 ---> " + responseEntity.toString());
    }

    private ResponseEntity<String> sendNotification(Refund refund,MultiValueMap<String, String> headers) {
        ResponseEntity<String> responseEntity;
        log.info("Send Notification1 ---> " + refund.toString());
        log.info("Send Notification4 ---> " + refund.getRefundInstructionType());
        log.info("Send Notification5 ---> " + refund.getReason());
        log.info("Send Notification6 ---> " + refund.getContactDetails());
        log.info("Send Notification7 ---> " + refundsUtil.getTemplate(refund));

        if (EMAIL.name().equals(refund.getContactDetails().getNotificationType())) {
            ContactDetails newContact = ContactDetails.contactDetailsWith()
                .email(refund.getContactDetails().getEmail())
                .templateId(refundsUtil.getTemplate(refund))
                .notificationType(EMAIL.name())
                .build();
            refund.setContactDetails(newContact);
            refund.setNotificationSentFlag("email_not_sent");
            RefundNotificationEmailRequest refundNotificationEmailRequest = refundNotificationMapper
                .getRefundNotificationEmailRequestApproveJourney(refund);
            responseEntity = notificationService.postEmailNotificationData(headers,refundNotificationEmailRequest);
        } else {
            ContactDetails newContact = ContactDetails.contactDetailsWith()
                .templateId(refundsUtil.getTemplate(refund))
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
                .getRefundNotificationLetterRequestApproveJourney(refund);
            responseEntity = notificationService.postLetterNotificationData(headers,refundNotificationLetterRequestRequest);

        }
        log.info("Send Notification8 ---> " + responseEntity.toString());
        return responseEntity;
    }
}
