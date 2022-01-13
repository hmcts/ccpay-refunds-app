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

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NotificationServiceImpl implements NotificationService {

    @Autowired
    private RestTemplate restTemplateNotify;

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
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(new StringBuilder(notificationUrl).append(emailUrlPath).toString());
            return restTemplateNotify.exchange(builder.toUriString(), HttpMethod.POST,
                                               new HttpEntity<>(refundNotificationEmailRequest, getFormatedHeaders(headers)),String.class);
        } catch (HttpClientErrorException exception) {
            handleHttpClientErrorException(exception);
        } catch (HttpServerErrorException exception) {
            log.info("Notification service is unavailable. Please try again later.");
        }
        return new ResponseEntity<String>("Notification service is unavailable",HttpStatus.SERVICE_UNAVAILABLE);
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
        return new ResponseEntity<String>("Notification service is unavailable",HttpStatus.SERVICE_UNAVAILABLE);

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
        HttpStatus status = exception.getStatusCode();
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
}
