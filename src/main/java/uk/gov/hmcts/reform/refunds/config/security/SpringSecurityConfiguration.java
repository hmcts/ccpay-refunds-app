package uk.gov.hmcts.reform.refunds.config.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import uk.gov.hmcts.reform.auth.checker.core.RequestAuthorizer;
import uk.gov.hmcts.reform.auth.checker.core.service.Service;
import uk.gov.hmcts.reform.auth.checker.core.user.User;
import uk.gov.hmcts.reform.auth.checker.spring.serviceanduser.AuthCheckerServiceAndUserFilter;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;


@EnableWebSecurity
@Configuration
public class SpringSecurityConfiguration extends WebSecurityConfigurerAdapter {

    private static final Logger LOG = LoggerFactory.getLogger(SpringSecurityConfiguration.class);
    private static final String AUTHORISED_ROLE_PAYMENT = "payments";
    private static final String AUTHORISED_ROLE_CITIZEN = "citizen";

    private final AuthCheckerServiceAndUserFilter authCheckerFilter;

    @Autowired
    public SpringSecurityConfiguration(RequestAuthorizer<User> userRequestAuthorizer,
                                       RequestAuthorizer<Service> serviceRequestAuthorizer,
                                       AuthenticationManager authenticationManager) {
        super();
        authCheckerFilter = new AuthCheckerServiceAndUserFilter(serviceRequestAuthorizer, userRequestAuthorizer);
        authCheckerFilter.setAuthenticationManager(authenticationManager);
    }

    @Override
    public void configure(WebSecurity web) {
        web.ignoring().antMatchers(
            "/swagger-ui.html",
            "/webjars/springfox-swagger-ui/**",
            "/swagger-resources/**",
            "/v2/**",
            "/refdata/**",
            "/health",
            "/health/liveness",
            "/health/readiness",
            "/info",
            "/favicon.ico",
            "/mock-api/**",
            "/error",
            "/"
        );
    }

    @Override
    //@SuppressWarnings(value = "SPRING_CSRF_PROTECTION_DISABLED", justification = "It's safe to disable CSRF protection as application is not being hit directly from the browser")
    protected void configure(HttpSecurity http) {
        try {
            http.addFilter(authCheckerFilter)
                .sessionManagement().sessionCreationPolicy(STATELESS).and()
                .csrf().disable()
                .formLogin().disable()
                .logout().disable()
                .authorizeRequests()
                .antMatchers(HttpMethod.GET, "/refundstest").hasAnyAuthority(
                AUTHORISED_ROLE_PAYMENT,
                AUTHORISED_ROLE_CITIZEN
            )
                .anyRequest().authenticated();
        } catch (Exception e) {
            LOG.info("Error in ExternalApiSecurityConfigurationAdapter: {}", e);
        }
    }
}


