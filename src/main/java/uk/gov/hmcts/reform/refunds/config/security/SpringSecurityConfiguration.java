package uk.gov.hmcts.reform.refunds.config.security;

import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import uk.gov.hmcts.reform.authorisation.filters.ServiceAuthFilter;
import uk.gov.hmcts.reform.refunds.config.security.converter.RefundsJwtGrantedAuthoritiesConverter;
import uk.gov.hmcts.reform.refunds.config.security.exception.RefundsAccessDeniedHandler;
import uk.gov.hmcts.reform.refunds.config.security.exception.RefundsAuthenticationEntryPoint;
import uk.gov.hmcts.reform.refunds.config.security.filiters.ServiceAndUserAuthFilter;
import uk.gov.hmcts.reform.refunds.config.security.utils.SecurityUtils;
import uk.gov.hmcts.reform.refunds.config.security.validator.AudienceValidator;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;


@EnableWebSecurity
public class SpringSecurityConfiguration {

    private static final String AUTHORISED_REFUNDS_ROLE = "payments-refund";
    private static final String AUTHORISED_REFUNDS_APPROVER_ROLE = "payments-refund-approver";
    private static final String PAYMENTS_ROLE = "payments";

    @Configuration
    @Order(1)
    public static class ExternalApiSecurityConfigurationAdapter {

        private final ServiceAuthFilter serviceAuthFilter;
        private final RefundsAuthenticationEntryPoint refundsAuthenticationEntryPoint;
        private final RefundsAccessDeniedHandler refundsAccessDeniedHandler;

        @Autowired
        public ExternalApiSecurityConfigurationAdapter(final ServiceAuthFilter serviceAuthFilter,
                                                       final RefundsAuthenticationEntryPoint refundsAuthenticationEntryPoint,
                                                       final RefundsAccessDeniedHandler refundsAccessDeniedHandler) {
            super();
            this.serviceAuthFilter = serviceAuthFilter;
            this.refundsAuthenticationEntryPoint = refundsAuthenticationEntryPoint;
            this.refundsAccessDeniedHandler = refundsAccessDeniedHandler;
        }


        public WebSecurityCustomizer webSecurityCustomizer() {
            return web -> web.ignoring().requestMatchers(
                "/favicon.ico",
                "/health",
                "/health/liveness",
                "/health/readiness",
                "/info",
                "/mock-api/**",
                "/refdata/**",
                "/swagger-resources",
                "/swagger-resources/**",
                "/swagger-ui.html",
                "/swagger-ui/**",
                "/v3/**",
                "/webjars/springfox-swagger-ui/**",
                "/"
            );
        }

        protected SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
            http
                .addFilterBefore(serviceAuthFilter, BearerTokenAuthenticationFilter.class)
                .sessionManagement().sessionCreationPolicy(STATELESS).and().anonymous().disable()
                .csrf().disable()
                .formLogin().disable()
                .logout().disable()
                .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers(HttpMethod.GET, "/refundstest").permitAll()
                    .requestMatchers(HttpMethod.PATCH, "/refund/*").permitAll()
                    .requestMatchers("/jobs/**").permitAll()
                )
                .exceptionHandling().accessDeniedHandler(refundsAccessDeniedHandler)
                .authenticationEntryPoint(refundsAuthenticationEntryPoint);
            return http.build();
        }

    }

    @Configuration
    @Order(2)
    public static class InternalApiSecurityConfigurationAdapter {

        private final ServiceAuthFilter serviceAuthFilter;
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
                                                       final SecurityUtils securityUtils,
                                                       final RefundsAuthenticationEntryPoint refundsAuthenticationEntryPoint,
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

        @Bean
        public WebSecurityCustomizer webSecurityCustomizer() {
            return web -> web.ignoring().requestMatchers(
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
                "/mock-api/**"
            );
        }

        @SuppressWarnings(value = "SPRING_CSRF_PROTECTION_DISABLED",
            justification = "It's safe to disable CSRF protection as application is not being hit directly from the browser")
        protected SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

            http
                .addFilterAfter(serviceAndUserAuthFilter, BearerTokenAuthenticationFilter.class)
                .addFilterBefore(serviceAuthFilter, BearerTokenAuthenticationFilter.class)
                .sessionManagement().sessionCreationPolicy(STATELESS).and()
                .csrf().disable()
                .formLogin().disable()
                .logout().disable()
                .authorizeHttpRequests(authorize -> authorize
                    .requestMatchers(HttpMethod.POST, "/refund").hasAnyAuthority(AUTHORISED_REFUNDS_APPROVER_ROLE,AUTHORISED_REFUNDS_ROLE)
                    .requestMatchers(HttpMethod.PATCH,"/refund/resubmit/*").hasAnyAuthority(AUTHORISED_REFUNDS_APPROVER_ROLE,AUTHORISED_REFUNDS_ROLE)
                    .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
                    .requestMatchers(HttpMethod.GET, "/refunds/**").permitAll()
                    .requestMatchers(HttpMethod.GET,"/refund/payment-failure-report").permitAll()
                    .requestMatchers(HttpMethod.GET,"/refund").hasAnyAuthority(AUTHORISED_REFUNDS_APPROVER_ROLE,AUTHORISED_REFUNDS_ROLE,PAYMENTS_ROLE)
                    .requestMatchers(HttpMethod.GET,"/refund/**").hasAnyAuthority(AUTHORISED_REFUNDS_APPROVER_ROLE,AUTHORISED_REFUNDS_ROLE)
                    .requestMatchers(HttpMethod.PATCH,"/refund/*/action/*").hasAuthority(AUTHORISED_REFUNDS_APPROVER_ROLE)
                    .requestMatchers(HttpMethod.PATCH,"/payment/**").permitAll()
                    .requestMatchers("/error").permitAll()
                    .anyRequest().authenticated()
                )
                .oauth2ResourceServer()
                .jwt()
                .jwtAuthenticationConverter(jwtAuthenticationConverter)
                .and()
                .and()
                .oauth2Client()
                .and()
                .exceptionHandling().accessDeniedHandler(refundsAccessDeniedHandler)
                .authenticationEntryPoint(refundsAuthenticationEntryPoint);
            return http.build();
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
