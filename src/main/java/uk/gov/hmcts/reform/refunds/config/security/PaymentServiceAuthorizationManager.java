package uk.gov.hmcts.reform.refunds.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.authorisation.validators.AuthTokenValidator;

import java.util.Arrays;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
public class PaymentServiceAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final AuthTokenValidator authTokenValidator;
    private final Set<String> authorisedServices;

    public PaymentServiceAuthorizationManager(
        AuthTokenValidator authTokenValidator,
        @Value("${idam.s2s-authorised.services}") String authorisedServicesConfig) {
        this.authTokenValidator = authTokenValidator;
        this.authorisedServices = Arrays.stream(authorisedServicesConfig.split(","))
            .map(String::trim)
            .filter(service -> !service.isEmpty())
            .collect(Collectors.toSet());
    }

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication,
                                       RequestAuthorizationContext context) {
        String token = context.getRequest().getHeader("ServiceAuthorization");
        if (token == null || token.isBlank()) {
            return new AuthorizationDecision(false);
        }

        try {
            String service = authTokenValidator.getServiceName(token.startsWith("Bearer ") ? token : "Bearer " + token);
            return new AuthorizationDecision(authorisedServices.contains(service));
        } catch (RuntimeException exception) {
            return new AuthorizationDecision(false);
        }
    }
}
