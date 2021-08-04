package uk.gov.hmcts.reform.refunds.config.security;

import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter;
import uk.gov.hmcts.reform.authorisation.filters.ServiceAuthFilter;
import uk.gov.hmcts.reform.refunds.config.security.converter.RefundsJwtGrantedAuthoritiesConverter;
import uk.gov.hmcts.reform.refunds.config.security.exception.RefundsAccessDeniedHandler;
import uk.gov.hmcts.reform.refunds.config.security.exception.RefundsAuthenticationEntryPoint;
import uk.gov.hmcts.reform.refunds.config.security.filiters.ServiceAndUserAuthFilter;
import uk.gov.hmcts.reform.refunds.config.security.utils.SecurityUtils;
import uk.gov.hmcts.reform.refunds.config.security.validator.AudienceValidator;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;
import javax.inject.Inject;


import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;


@EnableWebSecurity
@Configuration
public class SpringSecurityConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(SpringSecurityConfiguration.class);
//    private static final String AUTHORISED_ROLE_REFUNDS = "payments";

    @Configuration
    @Order(1)
    public static class ExternalApiSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {

        private final ServiceAuthFilter serviceAuthFilter;
        private final RefundsAuthenticationEntryPoint refundsAuthenticationEntryPoint;
        private final RefundsAccessDeniedHandler refundsAccessDeniedHandler;

        @Autowired
        public ExternalApiSecurityConfigurationAdapter(final ServiceAuthFilter serviceAuthFilter, final RefundsAuthenticationEntryPoint refundsAuthenticationEntryPoint,
                                                       final RefundsAccessDeniedHandler refundsAccessDeniedHandler) {
            super();
            this.serviceAuthFilter = serviceAuthFilter;
            this.refundsAuthenticationEntryPoint = refundsAuthenticationEntryPoint;
            this.refundsAccessDeniedHandler = refundsAccessDeniedHandler;
        }


        @Override
        public void configure(WebSecurity web) {
            web.ignoring().antMatchers(
                "/swagger-ui.html",
                "/webjars/springfox-swagger-ui/**",
                "/swagger-resources",
                "/swagger-resources/**",
                "/v2/**",
                "/refdata/**",
                "/health",
                "/health/liveness",
                "/health/readiness",
                "/info",
                "/favicon.ico",
                "/mock-api/**",
                "/"
            );
        }

        @Override
        protected void configure(HttpSecurity http) {
            try {

                http
                    .addFilterBefore(serviceAuthFilter, BearerTokenAuthenticationFilter.class)
                    .sessionManagement().sessionCreationPolicy(STATELESS).and().anonymous().disable()
                    .csrf().disable()
                    .formLogin().disable()
                    .logout().disable()
                    .requestMatchers()
                    .antMatchers(HttpMethod.GET, "/refundstest")
                    .antMatchers(HttpMethod.GET, "/refund/reasons")
                    .and()
                    .exceptionHandling().accessDeniedHandler(refundsAccessDeniedHandler)
                    .authenticationEntryPoint(refundsAuthenticationEntryPoint);

            } catch (Exception e) {
                LOG.info("Error in ExternalApiSecurityConfigurationAdapter: {}", e);
            }
        }

    }

    @Configuration
    @Order(2)
    public static class InternalApiSecurityConfigurationAdapter extends WebSecurityConfigurerAdapter {

        private static final Logger LOG = LoggerFactory.getLogger(SpringSecurityConfiguration.class);
        private final ServiceAuthFilter serviceAuthFilter;

//        @Value("${oidc.issuer}")
//        private String issuerOverride;
        private final ServiceAndUserAuthFilter serviceAndUserAuthFilter;
        private final JwtAuthenticationConverter jwtAuthenticationConverter;
        private final RefundsAuthenticationEntryPoint refundsAuthenticationEntryPoint;
        private final RefundsAccessDeniedHandler refundsAccessDeniedHandler;
        @Value("${spring.security.oauth2.client.provider.oidc.issuer-uri}")
        private String issuerUri;
        @Value("${oidc.audience-list}")
        private String[] allowedAudiences;

        @Inject
        public InternalApiSecurityConfigurationAdapter(final RefundsJwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter,
                                                       final ServiceAuthFilter serviceAuthFilter,
                                                       final Function<HttpServletRequest, Optional<String>> userIdExtractor,
                                                       final Function<HttpServletRequest, Collection<String>> authorizedRolesExtractor,
                                                       final SecurityUtils securityUtils, final RefundsAuthenticationEntryPoint refundsAuthenticationEntryPoint,
                                                       final RefundsAccessDeniedHandler refundsAccessDeniedHandler) {
            super();
            this.serviceAndUserAuthFilter = new ServiceAndUserAuthFilter(
                userIdExtractor, authorizedRolesExtractor, securityUtils);
            this.serviceAuthFilter = serviceAuthFilter;
            jwtAuthenticationConverter = new JwtAuthenticationConverter();
            jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwtGrantedAuthoritiesConverter);
            this.refundsAuthenticationEntryPoint = refundsAuthenticationEntryPoint;
            this.refundsAccessDeniedHandler = refundsAccessDeniedHandler;
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
                "/",
                "/**/*",
                "/refund"
            );
        }

        @Override
        @SuppressWarnings(value = "SPRING_CSRF_PROTECTION_DISABLED", justification = "It's safe to disable CSRF protection as application is not being hit directly from the browser")
        protected void configure(HttpSecurity http) {
            try {
                http
                    .addFilterAfter(serviceAndUserAuthFilter, BearerTokenAuthenticationFilter.class)
                    .addFilterBefore(serviceAuthFilter, BearerTokenAuthenticationFilter.class)
                    .sessionManagement().sessionCreationPolicy(STATELESS).and()
                    .csrf().disable()
                    .formLogin().disable()
                    .logout().disable()
                    .authorizeRequests()
//                    .antMatchers(HttpMethod.POST, "/refunds").hasAnyAuthority(AUTHORISED_ROLE_REFUNDS)
                    .antMatchers(HttpMethod.GET, "/api/**").permitAll()
                    .antMatchers("/error").permitAll()
                    .anyRequest().authenticated()
                    .and()
                    .oauth2ResourceServer()
                    .jwt()
                    .jwtAuthenticationConverter(jwtAuthenticationConverter)
                    .and()
                    .and()
                    .oauth2Client()
                    .and()
                    .exceptionHandling().accessDeniedHandler(refundsAccessDeniedHandler)
                    .authenticationEntryPoint(refundsAuthenticationEntryPoint)
                ;

            } catch (Exception e) {
                LOG.info("Error in InternalApiSecurityConfigurationAdapter: {}", e);
            }
        }

        @Bean
        @SuppressWarnings("unchecked")
        JwtDecoder jwtDecoder() {
            NimbusJwtDecoder jwtDecoder = (NimbusJwtDecoder)
                JwtDecoders.fromOidcIssuerLocation(issuerUri);

            OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(Arrays.asList(allowedAudiences));

            OAuth2TokenValidator<Jwt> withTimestamp = new JwtTimestampValidator();

            // Commented issuer validation as confirmed by IDAM
            /* OAuth2TokenValidator<Jwt> withIssuer = new JwtIssuerValidator(issuerOverride);*/
            OAuth2TokenValidator<Jwt> withAudience = new DelegatingOAuth2TokenValidator<>(
                withTimestamp,
                audienceValidator
            );
            jwtDecoder.setJwtValidator(withAudience);

            return jwtDecoder;
        }
    }

}
