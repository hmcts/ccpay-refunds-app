package uk.gov.hmcts.reform.refunds.config;

import org.jboss.aerogear.security.otp.Totp;
import org.springframework.stereotype.Component;

@Component
public class OneTimePasswordFactory {
    public String validOneTimePassword(String secret) {
        Totp totp = new Totp(secret);
        return totp.now();
    }
}
