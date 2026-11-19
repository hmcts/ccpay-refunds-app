package uk.gov.hmcts.reform.refunds.config.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.hmcts.reform.authorisation.filters.ServiceAuthFilter;
import uk.gov.hmcts.reform.refunds.config.security.converter.RefundsJwtGrantedAuthoritiesConverter;
import uk.gov.hmcts.reform.refunds.config.security.exception.RefundsAccessDeniedHandler;
import uk.gov.hmcts.reform.refunds.config.security.exception.RefundsAuthenticationEntryPoint;
import uk.gov.hmcts.reform.refunds.config.security.utils.SecurityUtils;
import uk.gov.hmcts.reform.refunds.support.LocalHttpStubServer;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;

/*
 * Runs OIDC client discovery and the production JwtDecoder against a local issuer, using real
 * RSA-signed tokens: valid tokens keep their claims; wrong-audience, expired, untrusted-signature
 * and malformed tokens are each rejected for that reason.
 * Protects against the app failing to start (discovery), locking out every user, or accepting
 * tokens it should refuse. Other tests mock JwtDecoder, so they bypass Spring Security, Nimbus
 * and json-smart entirely.
 */
class OidcDependencyCompatibilityTest {

    private static RSAKey signingKey;
    private static RSAKey untrustedKey;
    private LocalHttpStubServer server;
    private String issuer;

    @BeforeAll
    static void generateKeys() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
        untrustedKey = new RSAKeyGenerator(2048).keyID("test-key").generate();
    }

    @BeforeEach
    void publishProviderMetadataAndKeys() throws Exception {
        server = new LocalHttpStubServer();
        issuer = server.uri("").toString();
        server.respond("/.well-known/openid-configuration", 200,
            ("{\"issuer\":\"%1$s\",\"authorization_endpoint\":\"%1$s/authorize\",\"token_endpoint\":\"%1$s/token\","
                + "\"jwks_uri\":\"%1$s/jwks\",\"response_types_supported\":[\"code\"],\"subject_types_supported\":[\"public\"],"
                + "\"id_token_signing_alg_values_supported\":[\"RS256\"],\"scopes_supported\":[\"openid\",\"profile\",\"roles\"],"
                + "\"claims_supported\":[\"sub\",\"name\",\"roles\"]}")
                .formatted(issuer));
        server.respond("/jwks", 200, new JWKSet(signingKey.toPublicJWK()).toString());
    }

    @AfterEach
    void stopServer() {
        server.close();
    }

    @Test
    void discoversClientRegistrationUsingNimbusOidcAndJsonSmart() throws Exception {
        ClientRegistration registration = ClientRegistrations.fromIssuerLocation(issuer)
            .registrationId("oidc").clientId("refunds").clientSecret("test-secret")
            .scope("openid", "profile", "roles").build();

        assertThat(registration.getProviderDetails().getIssuerUri()).isEqualTo(issuer);
        assertThat(registration.getProviderDetails().getJwkSetUri()).isEqualTo(issuer + "/jwks");
        assertThat(registration.getProviderDetails().getTokenUri()).isEqualTo(issuer + "/token");
        assertThat(registration.getScopes()).containsExactlyInAnyOrder("openid", "profile", "roles");
        assertThat(registration.getProviderDetails().getConfigurationMetadata().get("claims_supported"))
            .isEqualTo(List.of("sub", "name", "roles"));
        assertThat(server.nextRequest().uri().getPath()).isEqualTo("/.well-known/openid-configuration");
    }

    @Test
    void verifiesSignedTokenAndPreservesClaimsUsingProductionDecoder() throws Exception {
        Jwt jwt = decoder().decode(token(signingKey, "paybubble", Instant.now().plusSeconds(300)));

        assertThat(jwt.getSubject()).isEqualTo("user-123");
        assertThat(jwt.getAudience()).containsExactly("paybubble");
        assertThat(jwt.getClaimAsString("tokenName")).isEqualTo("access_token");
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("payments-refund");
    }

    @Test
    void rejectsTokenWithWrongAudience() throws Exception {
        JwtDecoder decoder = decoder();
        String token = token(signingKey, "another-application", Instant.now().plusSeconds(300));
        assertThatExceptionOfType(JwtValidationException.class).isThrownBy(() -> decoder.decode(token))
            .satisfies(error -> assertThat(error.getErrors()).extracting(OAuth2Error::getDescription)
                .containsExactly("The required audience is missing"));
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        JwtDecoder decoder = decoder();
        String token = token(signingKey, "paybubble", Instant.now().minusSeconds(300));
        assertThatExceptionOfType(JwtValidationException.class).isThrownBy(() -> decoder.decode(token))
            .satisfies(error -> assertThat(error.getErrors()).extracting(OAuth2Error::getDescription)
                .singleElement().asString().startsWith("Jwt expired at"));
    }

    @Test
    void rejectsTokenWithUntrustedSignature() throws Exception {
        JwtDecoder decoder = decoder();
        String token = token(untrustedKey, "paybubble", Instant.now().plusSeconds(300));
        assertThatExceptionOfType(BadJwtException.class).isThrownBy(() -> decoder.decode(token))
            .withMessageContaining("Invalid signature");
    }

    @Test
    void rejectsMalformedToken() {
        JwtDecoder decoder = decoder();
        assertThatExceptionOfType(BadJwtException.class).isThrownBy(() -> decoder.decode("not-a-jwt"))
            .withMessageContaining("Malformed token");
    }

    private JwtDecoder decoder() {
        SpringSecurityConfiguration configuration = new SpringSecurityConfiguration(
            mock(RefundsJwtGrantedAuthoritiesConverter.class), mock(ServiceAuthFilter.class),
            request -> Optional.empty(), request -> List.of(), mock(SecurityUtils.class),
            mock(RefundsAuthenticationEntryPoint.class), mock(RefundsAccessDeniedHandler.class),
            (authentication, context) -> new AuthorizationDecision(false));
        ReflectionTestUtils.setField(configuration, "issuerUri", issuer);
        ReflectionTestUtils.setField(configuration, "allowedAudiences", new String[]{"paybubble"});
        return configuration.jwtDecoder();
    }

    private String token(RSAKey key, String audience, Instant expiresAt) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
            new JWTClaimsSet.Builder().issuer(issuer).subject("user-123").audience(audience)
                .issueTime(Date.from(expiresAt.minusSeconds(600))).expirationTime(Date.from(expiresAt))
                .claim("tokenName", "access_token").claim("roles", List.of("payments-refund")).build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
}
