package uk.gov.hmcts.reform.refunds.config.security.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.authorisation.generators.AuthTokenGenerator;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;
import uk.gov.hmcts.reform.refunds.config.security.idam.IdamRepository;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SecurityUtils {
    private final AuthTokenGenerator authTokenGenerator;
    private final IdamRepository idamRepository;

    @Autowired
    public SecurityUtils(AuthTokenGenerator authTokenGenerator, IdamRepository idamRepository) {
        this.authTokenGenerator = authTokenGenerator;
        this.idamRepository = idamRepository;
    }

    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && getAuthorities(authentication).findAny().isPresent();
    }

    private static Stream<String> getAuthorities(Authentication authentication) {
        Collection<? extends GrantedAuthority> authorities;
        //added condition for claims
        if (authentication instanceof JwtAuthenticationToken
            && ((JwtAuthenticationToken) authentication).getToken().getClaims().containsKey("roles")) {
            authorities = extractAuthorityFromClaims(((JwtAuthenticationToken) authentication).getToken().getClaims());
        } else {
            authorities = authentication.getAuthorities();
        }
        return authorities.stream()
            .map(GrantedAuthority::getAuthority);
    }

    @SuppressWarnings("unchecked")
    public static List<GrantedAuthority> extractAuthorityFromClaims(Map<String, Object> claims) {
        if (!Optional.ofNullable(claims).isPresent() && !Optional.ofNullable(claims.get("roles")).isPresent()) {
            throw new InsufficientAuthenticationException("No roles can be extracted from claims "
                                                              + "most probably due to insufficient scopes provided");
        }

        return ((List<String>) claims.get("roles"))
            .stream()
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());
    }

    public HttpHeaders authorizationHeaders() {
        final HttpHeaders headers = new HttpHeaders();
        headers.add("ServiceAuthorization", authTokenGenerator.generate());
        headers.add("user-id", getUserId());
        headers.add("user-roles", getUserRolesHeader());

        if (SecurityContextHolder.getContext().getAuthentication() != null) {
            headers.add(HttpHeaders.AUTHORIZATION, getUserBearerToken());
        }
        return headers;
    }

    public HttpHeaders userAuthorizationHeaders() {
        final HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, getUserBearerToken());
        return headers;
    }

    public UserInfo getUserInfo() {
        return idamRepository.getUserInfo(getUserToken());
    }

    public String getUserId() {
        return getUserInfo().getUid();
    }

    public String getUserToken() {
        Jwt jwt = (Jwt) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return jwt.getTokenValue();
    }

    private String getUserBearerToken() {
        return "Bearer " + getUserToken();
    }

    public String getUserRolesHeader() {
        Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        return authorities.stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.joining(","));
    }
}
