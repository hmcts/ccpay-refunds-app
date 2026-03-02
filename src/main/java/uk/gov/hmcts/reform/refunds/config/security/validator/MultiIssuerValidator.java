package uk.gov.hmcts.reform.refunds.config.security.validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class MultiIssuerValidator implements OAuth2TokenValidator<Jwt> {

    private static final Logger LOG = LoggerFactory.getLogger(MultiIssuerValidator.class);

    private final OAuth2Error error = new OAuth2Error("invalid_token",
        "The required issuer is missing or invalid", null);

    private final List<String> validIssuers;

    public MultiIssuerValidator(List<String> validIssuers) {
        Assert.notEmpty(validIssuers, "Valid issuers list must not be null or empty.");
        this.validIssuers = validIssuers.stream()
            .filter(Objects::nonNull)
            .filter(s -> !s.isBlank())
            .map(MultiIssuerValidator::normalizeIssuer)
            .distinct()
            .collect(Collectors.toList());
        Assert.notEmpty(this.validIssuers, "At least one non-blank issuer must be configured.");
        LOG.info("MultiIssuerValidator configured with valid issuers: {}", this.validIssuers);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String issuer = jwt.getClaimAsString(JwtClaimNames.ISS);
        if (issuer != null) {
            issuer = normalizeIssuer(issuer);
        }
        if (issuer != null && validIssuers.contains(issuer)) {
            return OAuth2TokenValidatorResult.success();
        }
        LOG.warn("Token issuer '{}' does not match any valid issuers: {}", issuer, validIssuers);
        return OAuth2TokenValidatorResult.failure(error);
    }

    private static String normalizeIssuer(String issuer) {
        if (issuer == null) {
            return null;
        }
        String normalized = issuer.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
