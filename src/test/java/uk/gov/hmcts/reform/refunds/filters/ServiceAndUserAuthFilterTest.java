package uk.gov.hmcts.reform.refunds.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;
import uk.gov.hmcts.reform.refunds.config.security.authcheckerconfiguration.AuthCheckerConfiguration;
import uk.gov.hmcts.reform.refunds.config.security.filiters.ServiceAndUserAuthFilter;
import uk.gov.hmcts.reform.refunds.config.security.utils.SecurityUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.OK;

@RunWith(MockitoJUnitRunner.class)
public class ServiceAndUserAuthFilterTest {
    @Mock
    private SecurityUtils securityUtils;
    private ServiceAndUserAuthFilter filter;
    private MockHttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;

    @Mock
    SecurityContext securityContext;

    @Mock
    Authentication authentication;

    @Before
    public void setUp() {
        AuthCheckerConfiguration config = new AuthCheckerConfiguration();
        filter = new ServiceAndUserAuthFilter(config.userIdExtractor(),
                                              config.authorizedRolesExtractor(), securityUtils);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = new MockFilterChain();

        Mockito.when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    public void shouldReturn200ResponseWhenRoleMatches() throws Exception {
        request.setRequestURI("/refunds/");
        when(SecurityContextHolder.getContext().getAuthentication()).thenReturn(getJwtAuthenticationTokenBasedOnRoles("payments-refund"));
        when(securityUtils.getUserInfo()).thenReturn(getUserInfoBasedOnUidRoles("user123", "payments-refund"));

        filter.doFilterInternal(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(OK.value());
    }

    @Test
    public void shouldReturn403ResponseWhenRoleIsInvalid() throws Exception {
        request.setRequestURI("/refunds/");
        when(SecurityContextHolder.getContext().getAuthentication()).thenReturn(getJwtAuthenticationTokenBasedOnRoles("payments"));
        when(securityUtils.getUserInfo()).thenReturn(getUserInfoBasedOnUidRoles("user123", "payments-invalid-role"));

        filter.doFilterInternal(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(FORBIDDEN.value());
        assertTrue(StringUtils.containsIgnoreCase(((MockHttpServletResponse)response).getErrorMessage(),
                                                         "Access Denied Current user roles are : [payments-invalid-role]"));
    }

    @Test
    public void shouldReturn403RWhenNoRolesPresentForUserInfo() throws Exception {
        request.setRequestURI("/refunds/");
        when(SecurityContextHolder.getContext().getAuthentication()).thenReturn(getJwtAuthenticationTokenBasedOnRoles("payments"));
        when(securityUtils.getUserInfo()).thenReturn(getUserInfoBasedOnUidRoles("user123", null));

        filter.doFilterInternal(request, response, filterChain);
        assertThat(response.getStatus()).isEqualTo(FORBIDDEN.value());
        assertTrue(StringUtils.containsIgnoreCase(((MockHttpServletResponse)response).getErrorMessage(),
                                                         "Access Denied Current user roles are : [null]"));
    }

    public static UserInfo getUserInfoBasedOnUidRoles(String uid, String roles) {
        return UserInfo.builder()
            .uid(uid)
            .roles(Collections.singletonList(roles))
            .build();
    }

    private JwtAuthenticationToken getJwtAuthenticationTokenBasedOnRoles(String authority) {
        List<String> stringGrantedAuthority = new ArrayList<>();
        stringGrantedAuthority.add(authority);

        Map<String,Object> claimsMap = new ConcurrentHashMap<>();
        claimsMap.put("roles", stringGrantedAuthority);

        Map<String,Object> headersMap = new ConcurrentHashMap<>();
        headersMap.put("authorisation","test-token");

        Jwt jwt = new Jwt("test_token",null,null,headersMap,claimsMap);
        return new JwtAuthenticationToken(jwt);
    }





}

