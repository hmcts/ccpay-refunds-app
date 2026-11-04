package uk.gov.hmcts.reform.refunds.config.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.authorisation.exceptions.InvalidTokenException;
import uk.gov.hmcts.reform.authorisation.filters.ServiceAuthFilter;
import uk.gov.hmcts.reform.authorisation.validators.AuthTokenValidator;
import uk.gov.hmcts.reform.refunds.config.security.converter.RefundsJwtGrantedAuthoritiesConverter;
import uk.gov.hmcts.reform.refunds.config.security.exception.RefundsAccessDeniedHandler;
import uk.gov.hmcts.reform.refunds.config.security.exception.RefundsAuthenticationEntryPoint;
import uk.gov.hmcts.reform.refunds.config.security.utils.SecurityUtils;
import uk.gov.hmcts.reform.refunds.controllers.RefundsActionController;
import uk.gov.hmcts.reform.refunds.services.RefundReviewService;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RefundsActionController.class, properties = {
    "idam.s2s-authorised.services=payment_app,ccpay_gw",
    "oidc.audience-list=refunds",
    "spring.security.oauth2.client.provider.oidc.issuer-uri=https://idam.example.test"
})
@Import({PaymentServiceAuthorizationManager.class,
    RefundsAuthenticationEntryPoint.class, RefundsAccessDeniedHandler.class,
    RefundsActionController.class})
@ContextConfiguration(classes = {
    SpringSecurityConfiguration.class, PaymentServiceSecurityTest.ServiceSecurityConfiguration.class
})
@ActiveProfiles("payment-service-security-test")
class PaymentServiceSecurityTest {

    private static final String PAYMENT_REFERENCE = "RC-1111-2222-3333-4444";
    private static final String CANCEL_PATH = "/payment/" + PAYMENT_REFERENCE + "/action/cancel";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthTokenValidator authTokenValidator;

    @MockitoBean
    private RefundReviewService refundReviewService;

    @MockitoBean
    private RefundsJwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter;

    @MockitoBean
    private RefundStatusUpdateAuthorizationManager refundStatusUpdateAuthorizationManager;

    @MockitoBean
    private SecurityUtils securityUtils;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @Test
    void shouldAllowPaymentServiceToCancelWithoutUserToken() throws Exception {
        when(authTokenValidator.getServiceName("Bearer valid-service-token")).thenReturn("payment_app");
        when(refundReviewService.cancelRefunds(any(), eq(PAYMENT_REFERENCE)))
            .thenReturn(ResponseEntity.ok("Refund cancelled"));

        mockMvc.perform(patch(CANCEL_PATH).header("ServiceAuthorization", "Bearer valid-service-token"))
            .andExpect(status().isOk())
            .andExpect(content().string("Refund cancelled"));

        verifyNoInteractions(jwtDecoder, securityUtils);
    }

    @Test
    void shouldAllowUnprefixedServiceToken() throws Exception {
        when(authTokenValidator.getServiceName("Bearer valid-service-token")).thenReturn("payment_app");
        when(refundReviewService.cancelRefunds(any(), eq(PAYMENT_REFERENCE)))
            .thenReturn(ResponseEntity.ok("Refund cancelled"));

        mockMvc.perform(patch(CANCEL_PATH).header("ServiceAuthorization", "valid-service-token"))
            .andExpect(status().isOk());
    }

    @Test
    void shouldUseServiceAuthenticationWhenUserHeaderIsAlsoPresent() throws Exception {
        when(authTokenValidator.getServiceName("Bearer valid-service-token")).thenReturn("payment_app");
        when(refundReviewService.cancelRefunds(any(), eq(PAYMENT_REFERENCE)))
            .thenReturn(ResponseEntity.ok("Refund cancelled"));

        mockMvc.perform(patch(CANCEL_PATH)
                .header("ServiceAuthorization", "Bearer valid-service-token")
                .header("Authorization", "Bearer user-token"))
            .andExpect(status().isOk());

        verifyNoInteractions(jwtDecoder, securityUtils);
    }

    @Test
    void shouldRejectCancellationWithoutServiceToken() throws Exception {
        mockMvc.perform(patch(CANCEL_PATH)).andExpect(status().isUnauthorized());

        verifyNoInteractions(refundReviewService);
    }

    @Test
    void shouldRejectUserTokenWithoutServiceToken() throws Exception {
        mockMvc.perform(patch(CANCEL_PATH).header("Authorization", "Bearer user-token"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(refundReviewService);
    }

    @Test
    void shouldRejectInvalidServiceToken() throws Exception {
        when(authTokenValidator.getServiceName("Bearer invalid-token"))
            .thenThrow(new InvalidTokenException("Invalid token"));

        mockMvc.perform(patch(CANCEL_PATH).header("ServiceAuthorization", "Bearer invalid-token"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(refundReviewService);
    }

    @Test
    void shouldRejectServiceOutsideAllowlist() throws Exception {
        when(authTokenValidator.getServiceName("Bearer other-service-token")).thenReturn("untrusted_service");

        mockMvc.perform(patch(CANCEL_PATH).header("ServiceAuthorization", "Bearer other-service-token"))
            .andExpect(status().isForbidden());

        verifyNoInteractions(refundReviewService);
    }

    @Test
    void shouldRejectSpoofedCertificateHeaderWithoutServiceToken() throws Exception {
        mockMvc.perform(patch(CANCEL_PATH).header("X-ARR-ClientCertThumbprint", "WHITELISTED-THUMBPRINT"))
            .andExpect(status().isUnauthorized());

        verifyNoInteractions(refundReviewService);
    }

    @Test
    void shouldKeepServiceTestConfigurationOutOfOtherApplicationScans() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.setActiveProfiles("local", "test");
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.setEnvironment(environment);
        scanner.addIncludeFilter(new AssignableTypeFilter(ServiceSecurityConfiguration.class));

        assertTrue(scanner.findCandidateComponents("uk.gov.hmcts.reform.refunds").isEmpty());
    }

    // TestContextConfiguration also performs a broad component scan without Boot's test exclusions.
    @TestConfiguration
    @Profile("payment-service-security-test")
    static class ServiceSecurityConfiguration {

        @Bean
        ServiceAuthFilter serviceAuthFilter(AuthTokenValidator authTokenValidator) {
            return new ServiceAuthFilter(authTokenValidator, List.of("payment_app", "ccpay_gw"));
        }

        @Bean
        Function<HttpServletRequest, Optional<String>> userIdExtractor() {
            return request -> Optional.empty();
        }

        @Bean
        Function<HttpServletRequest, Collection<String>> authorizedRolesExtractor() {
            return request -> List.of();
        }
    }
}
