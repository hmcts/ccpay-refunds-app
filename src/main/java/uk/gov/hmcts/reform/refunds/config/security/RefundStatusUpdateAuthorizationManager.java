package uk.gov.hmcts.reform.refunds.config.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.authorisation.validators.AuthTokenValidator;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
public class RefundStatusUpdateAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String AUTHORISED_REFUNDS_ROLE = "payments-refund";
    private static final String AUTHORISED_REFUNDS_APPROVER_ROLE = "payments-refund-approver";
    private static final Set<String> AUTHORISED_REFUND_ROLES =
        Set.of(AUTHORISED_REFUNDS_APPROVER_ROLE, AUTHORISED_REFUNDS_ROLE);
    private static final String CCPAY_GW_MICROSERVICE = "ccpay_gw";
    private static final String BEARER_PREFIX = "Bearer ";

    private final AuthTokenValidator authTokenValidator;
    private final Set<String> authorisedServices;

    @Autowired
    public RefundStatusUpdateAuthorizationManager(
        AuthTokenValidator authTokenValidator,
        @Value("${idam.s2s-authorised.services}") String authorisedServicesConfig) {
        this.authTokenValidator = authTokenValidator;
        this.authorisedServices = Arrays.stream(authorisedServicesConfig.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toSet());
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication,
                                      RequestAuthorizationContext object) {
        Authentication currentAuth = authentication.get();

        if (hasUserAuthorisedRole(currentAuth)) {
            return new AuthorizationDecision(true);
        }

        HttpServletRequest request = object.getRequest();
        if (!isAuthenticatedUser(currentAuth) && authorisedGatewayServiceCall(request)) {
            return new AuthorizationDecision(true);
        }

        return new AuthorizationDecision(false);
    }

    private boolean hasUserAuthorisedRole(Authentication currentAuth) {
        return currentAuth instanceof JwtAuthenticationToken
            && currentAuth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(AUTHORISED_REFUND_ROLES::contains);
    }

    private boolean isAuthenticatedUser(Authentication currentAuth) {
        return currentAuth != null && !(currentAuth instanceof AnonymousAuthenticationToken);
    }

    private boolean authorisedGatewayServiceCall(HttpServletRequest request) {
        String serviceAuthorization = request.getHeader("ServiceAuthorization");
        if (serviceAuthorization == null) {
            return false;
        }

        String token = serviceAuthorization.startsWith("Bearer ")
            ? serviceAuthorization.substring(BEARER_PREFIX.length())
            : serviceAuthorization;

        final String microservice;
        try {
            microservice = authTokenValidator.getServiceName(token);
        } catch (RuntimeException ex) {
            return false;
        }

        return authorisedServices.contains(microservice) && CCPAY_GW_MICROSERVICE.equals(microservice);
    }
}
