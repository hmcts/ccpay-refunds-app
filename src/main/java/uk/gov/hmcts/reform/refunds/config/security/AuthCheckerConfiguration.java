package uk.gov.hmcts.reform.refunds.config.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Configuration
public class AuthCheckerConfiguration {

    @Value("#{'${trusted.s2s.service.names}'.split(',')}")
    private List<String> authorizedServices;

    @Bean
    public Function<HttpServletRequest, Optional<String>> userIdExtractor() {
        return (request) -> {
            Pattern pattern = Pattern.compile("^/users/([^/]+)/.+$");
            Matcher matcher = pattern.matcher(request.getRequestURI());
            boolean matched = matcher.find();
            if(matched){
                return Optional.of(matcher.group(1));
            }else {
                return Optional.empty();
            }
        };
    }

    @Bean
    public Function<HttpServletRequest, Collection<String>> authorizedRolesExtractor() {
        return (any) -> Arrays.asList("payments","citizen");
    }

    @Bean
    public Function<HttpServletRequest, Collection<String>> authorizedServicesExtractor() {
        return (any) -> authorizedServices;
    }
}
