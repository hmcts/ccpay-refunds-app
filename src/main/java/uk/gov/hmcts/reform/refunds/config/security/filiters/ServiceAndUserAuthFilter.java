package uk.gov.hmcts.reform.refunds.config.security.filiters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;
import uk.gov.hmcts.reform.refunds.config.security.exception.UnauthorizedException;
import uk.gov.hmcts.reform.refunds.config.security.utils.SecurityUtils;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.springframework.http.HttpStatus.FORBIDDEN;

public class ServiceAndUserAuthFilter extends OncePerRequestFilter {

    private static final Logger LOG = LoggerFactory.getLogger(ServiceAndUserAuthFilter.class);

    private final Function<HttpServletRequest, Optional<String>> userIdExtractor;
    private final Function<HttpServletRequest, Collection<String>> authorizedRolesExtractor;
    private final SecurityUtils securityUtils;

    public ServiceAndUserAuthFilter(Function<HttpServletRequest, Optional<String>> userIdExtractor,
                                    Function<HttpServletRequest, Collection<String>> authorizedRolesExtractor,
                                    SecurityUtils securityUtils) {
        super();
        this.userIdExtractor = userIdExtractor;
        this.authorizedRolesExtractor = authorizedRolesExtractor;
        this.securityUtils = securityUtils;
    }

    @Override
    public void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {

        Collection<String> authorizedRoles = authorizedRolesExtractor.apply(request);
        Optional<String> userIdOptional = userIdExtractor.apply(request);
        if (securityUtils.isAuthenticated() && (!authorizedRoles.isEmpty() || userIdOptional.isPresent())) {
            try {
                verifyRoleAndUserId(authorizedRoles, userIdOptional);
                LOG.info("User authentication is successful");
            } catch (UnauthorizedException ex) {
                LOG.warn("Unauthorised roles or userId in the request path", ex);
                response.sendError(FORBIDDEN.value(), " Access Denied " + ex.getMessage());
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private void verifyRoleAndUserId(Collection<String> authorizedRoles, Optional<String> userIdOptional) {
        UserInfo userInfo = securityUtils.getUserInfo();
        if (!authorizedRoles.isEmpty() && Collections.disjoint(authorizedRoles, userInfo.getRoles())) {

            Optional<List<String>> currentRolesOptional = Optional.ofNullable(userInfo.getRoles());
            if (currentRolesOptional.isPresent() && !currentRolesOptional.get().isEmpty()) {
                throw new UnauthorizedException("Current user roles are : " + currentRolesOptional.get()
                                                    + " While Authorised roles are only : " + authorizedRoles);
            }

        }

        if (userIdOptional.isPresent() && !userIdOptional.get().equalsIgnoreCase(userInfo.getUid())) {
            throw new UnauthorizedException("Unauthorised userId in the path");
        }
    }

}
