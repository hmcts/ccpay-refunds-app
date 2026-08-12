package uk.gov.hmcts.reform.refunds.config.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

import java.util.Set;
import java.util.function.Supplier;

import static org.springframework.http.HttpHeaders.AUTHORIZATION;

public class RefundStatusUpdateAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String AUTHORISED_REFUNDS_ROLE = "payments-refund";
    private static final String AUTHORISED_REFUNDS_APPROVER_ROLE = "payments-refund-approver";
    private static final Set<String> AUTHORISED_REFUND_ROLES =
        Set.of(AUTHORISED_REFUNDS_APPROVER_ROLE, AUTHORISED_REFUNDS_ROLE);

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication,
                                        RequestAuthorizationContext object) {
        Authentication currentAuth = authentication.get();

        boolean hasUserAuthorisedRole = currentAuth instanceof JwtAuthenticationToken
            && currentAuth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .anyMatch(AUTHORISED_REFUND_ROLES::contains);
        if (hasUserAuthorisedRole) {
            return new AuthorizationDecision(true);
        }

        HttpServletRequest request = object.getRequest();
        if (isServiceAuthentication(currentAuth, request)) {
            return new AuthorizationDecision(true);
        }

        return new AuthorizationDecision(false);
    }

    private boolean isServiceAuthentication(Authentication currentAuth, HttpServletRequest request) {
        boolean isUserTokenPresent = currentAuth instanceof JwtAuthenticationToken
            || currentAuth != null && !(currentAuth instanceof AnonymousAuthenticationToken);
        if (isUserTokenPresent) {
            return false;
        }
        return request.getHeader(AUTHORIZATION) == null
            && request.getHeader("ServiceAuthorization") != null;
    }
}
