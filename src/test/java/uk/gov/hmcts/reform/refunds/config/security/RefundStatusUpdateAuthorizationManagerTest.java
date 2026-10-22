package uk.gov.hmcts.reform.refunds.config.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import uk.gov.hmcts.reform.authorisation.validators.AuthTokenValidator;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefundStatusUpdateAuthorizationManagerTest {

    @Mock
    private AuthTokenValidator authTokenValidator;

    private RefundStatusUpdateAuthorizationManager authorizationManager;

    @BeforeEach
    void setUp() {
        authorizationManager = new RefundStatusUpdateAuthorizationManager(authTokenValidator, "ccpay_gw,payment_app");
    }

    @Test
    void shouldAddBearerPrefixWhenServiceHeaderDoesNotContainIt() {
        when(authTokenValidator.getServiceName("Bearer raw-s2s-token")).thenReturn("ccpay_gw");

        AuthorizationDecision decision = authorizationManager.check(
            () -> null,
            new RequestAuthorizationContext(createRequestWithServiceHeader("raw-s2s-token"))
        );

        assertNotNull(decision);
        assertTrue(isGranted(decision));
        verify(authTokenValidator).getServiceName("Bearer raw-s2s-token");
    }

    @Test
    void shouldKeepBearerPrefixWhenAlreadyPresent() {
        when(authTokenValidator.getServiceName("Bearer already-prefixed")).thenReturn("ccpay_gw");

        AuthorizationDecision decision = authorizationManager.check(
            () -> null,
            new RequestAuthorizationContext(createRequestWithServiceHeader("Bearer already-prefixed"))
        );

        assertNotNull(decision);
        assertTrue(isGranted(decision));
        verify(authTokenValidator).getServiceName("Bearer already-prefixed");
    }

    @Test
    void shouldAllowUserWithRefundRoleWithoutCallingServiceValidation() {
        Jwt jwt = Jwt.withTokenValue("jwt-token")
            .header("alg", "none")
            .claim("roles", List.of("payments-refund"))
            .build();
        Authentication authentication = new JwtAuthenticationToken(
            jwt,
            List.of(new SimpleGrantedAuthority("payments-refund"))
        );
        Supplier<Authentication> authenticationSupplier = () -> authentication;

        AuthorizationDecision decision = authorizationManager.check(
            authenticationSupplier,
            new RequestAuthorizationContext(createRequestWithServiceHeader("raw-s2s-token"))
        );

        assertNotNull(decision);
        assertTrue(isGranted(decision));
        verifyNoInteractions(authTokenValidator);
    }

    @Test
    void shouldRejectUnknownService() {
        when(authTokenValidator.getServiceName("Bearer raw-s2s-token")).thenReturn("payment_app");

        AuthorizationDecision decision = authorizationManager.check(
            () -> null,
            new RequestAuthorizationContext(createRequestWithServiceHeader("raw-s2s-token"))
        );

        assertNotNull(decision);
        assertFalse(isGranted(decision));
    }

    private MockHttpServletRequest createRequestWithServiceHeader(String serviceAuthorizationHeader) {
        MockHttpServletRequest request = new MockHttpServletRequest("PATCH", "/refund/RF-1234-1234-1234-1234");
        request.addHeader("ServiceAuthorization", serviceAuthorizationHeader);
        return request;
    }

    private boolean isGranted(AuthorizationDecision decision) {
        return decision != null && Boolean.TRUE.equals(decision.isGranted());
    }
}

