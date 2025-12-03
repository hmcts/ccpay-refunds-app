package uk.gov.hmcts.reform.refunds.functional.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
public class TestConfigProperties {

    @Autowired
    public Oauth2 oauth2;

    @Value("${test.url.refunds}")
    public String baseTestUrl;

    @Value("${test.url.payments}")
    public String basePaymentsUrl;

    @Value("${generated.user.email.pattern}")
    public String generatedUserEmailPattern;

    @Value("${test.user.password}")
    public String testUserPassword;

    @Value("${idam.api.url}")
    public String idamApiUrl;

    @Value("${bulkscan.api.url}")
    public String bulkscanApiUrl;

    @Value("${ccd.api.url}")
    public String ccdApiUrl;

    @Value("${s2s.url}")
    private String s2sBaseUrl;

    @Value("${s2s.service.paymentapp.name}")
    public String s2sServiceName;

    @Value("${s2s.service.paymentapp.secret}")
    public String s2sServiceSecret;

    @Value("${payments.account.existing.account.number}")
    public String existingAccountNumber;

    @Value("${payments.account.fake.account.number}")
    public String fakeAccountNumber;

    @Value("${mock.callback.url.endpoint}")
    public String mockCallBackUrl;

    @Value("${s2s.service.paybubble.name}")
    public String payBubbleS2SName;

    @Value("${s2s.service.paybubble.secret}")
    public String payBubbleS2SSecret;

    @Value("${s2s.service.cmc.name}")
    public String cmcS2SName;

    @Value("${s2s.service.cmc.secret}")
    public String cmcS2SSecret;

    @Value("${idam.paybubble.client.id}")
    public String idamPayBubbleClientId;

    @Value("${idam.paybubble.client.secret}")
    public String idamPayBubbleClientSecret;

    @Value("${idam.paybubble.redirect.uri}")
    public String idamPayBubbleClientRedirectUri;

    @Value("${notification.api.key}")
    public String notificationApiKey;

    @Value("${probate.caseworker.username}")
    public String probateCaseworkerUsername;

    @Value("${probate.caseworker.password}")
    public String probateCaseworkerPassword;


}
