package uk.gov.hmcts.reform.refunds.functional.config;

import feign.Feign;
import feign.FeignException;
import feign.jackson.JacksonEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class S2sTokenService {

    private final OneTimePasswordFactory oneTimePasswordFactory;
    private final S2sApi s2sApi;

    @Autowired
    public S2sTokenService(OneTimePasswordFactory oneTimePasswordFactory, TestConfigProperties testProps) {
        this.oneTimePasswordFactory = oneTimePasswordFactory;
        s2sApi = Feign.builder()
            .encoder(new JacksonEncoder())
            .target(S2sApi.class, testProps.getS2sBaseUrl());
    }

    public String getS2sToken(String microservice, String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("S2S secret is missing for microservice '" + microservice
                                               + "'. Ensure the relevant S2S_SERVICE_SECRET_* env var is set.");
        }

        String otp = oneTimePasswordFactory.validOneTimePassword(secret);

        try {
            String token = s2sApi.serviceToken(microservice, otp);
            if (token == null || token.isBlank()) {
                throw new IllegalStateException("Received empty S2S token for microservice '" + microservice + "'.");
            }
            return token;
        } catch (FeignException ex) {
            throw new IllegalStateException(
                "Failed to obtain S2S token for microservice '" + microservice + "' (status " + ex.status() + ")"
                    + ". Check S2S_URL and the service secret.",
                ex
            );
        }
    }
}
