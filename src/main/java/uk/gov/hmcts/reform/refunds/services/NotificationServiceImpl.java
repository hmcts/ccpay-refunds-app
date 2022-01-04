package uk.gov.hmcts.reform.refunds.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationEmailRequest;
import uk.gov.hmcts.reform.refunds.dtos.requests.RefundNotificationLetterRequest;

import java.util.Arrays;
import java.util.List;

public class NotificationServiceImpl implements NotificationService{

    @Autowired
    private RestTemplate restTemplateNotify;

    @Value("${notification.url}")
    private String notificationUrl;

    @Value("${notification.email-path}")
    private String emailPath;

    public static final String CONTENT_TYPE = "content-type";

    @Autowired
    private AuthTokenGenerator authTokenGenerator;

    @Override
    public ResponseEntity postEmailNotificationData(MultiValueMap<String, String> headers, RefundNotificationEmailRequest refundNotificationEmailRequest) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(new StringBuilder(notificationUrl).append(emailPath).toString());
        return restTemplateNotify.exchange(builder.toUriString(), HttpMethod.POST,new HttpEntity<>(refundNotificationEmailRequest, getFormatedHeaders(headers)),ResponseEntity.class);
    }

    @Override
    public ResponseEntity postLetterNotificationData(MultiValueMap<String, String> headers, RefundNotificationLetterRequest refundNotificationLetterRequest) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(new StringBuilder(notificationUrl).append(emailPath).toString());
        return restTemplateNotify.exchange(builder.toUriString(), HttpMethod.POST,new HttpEntity<>(refundNotificationLetterRequest, getFormatedHeaders(headers)),ResponseEntity.class);
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
