package uk.gov.hmcts.reform.refunds.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.WebServerException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationEmailRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationLetterRequest;
import uk.gov.hmcts.reform.refunds.exceptions.InvalidRefundNotificationResendRequestException;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Service
public class NotificationServiceImpl implements NotificationService {

    @Autowired
    private WebClient webClientNotify;

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

            Consumer<HttpHeaders> consumer = it -> it.addAll(getFormatedHeaders(headers));

            return webClientNotify
                .post()
                .uri(builder.toUriString())
                .headers(consumer)
                .body(refundNotificationEmailRequest, RefundNotificationEmailRequest.class)
                .retrieve()
                .toEntity(String.class)
                .block();

        } catch (HttpClientErrorException exception) {
            throw new InvalidRefundNotificationResendRequestException("Invalid Refund notification request.", exception);
        } catch (HttpServerErrorException exception) {
            log.error("Notification service is unavailable. Please try again later.", exception.getMessage(), exception);
        }
        return new ResponseEntity<>("Notification service is unavailable",HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Override
    public ResponseEntity<String> postLetterNotificationData(MultiValueMap<String, String> headers,
                                                             RefundNotificationLetterRequest refundNotificationLetterRequest) {

        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(new StringBuilder(notificationUrl).append(letterUrlPath).toString());

            Consumer<HttpHeaders> consumer = it -> it.addAll(getFormatedHeaders(headers));

            return webClientNotify
                .post()
                .uri(builder.toUriString())
                .headers(consumer)
                .body(Mono.just(refundNotificationLetterRequest), RefundNotificationLetterRequest.class)
                .retrieve()
                .toEntity(String.class)
                .block();

        } catch (WebClientException exception) {
            throw new InvalidRefundNotificationResendRequestException("Invalid Refund notification request.", exception);
        } catch (WebServerException exception) {
            log.error("Notification service is unavailable. Please try again later.", exception.getMessage(), exception);
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

}
