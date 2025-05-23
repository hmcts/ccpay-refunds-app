package uk.gov.hmcts.reform.refunds.config.security.converter;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import uk.gov.hmcts.reform.idam.client.models.UserInfo;
import uk.gov.hmcts.reform.refunds.config.security.idam.IdamRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames.ACCESS_TOKEN;

@Component
public class RefundsJwtGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    public static final String TOKEN_NAME = "tokenName";

    private final IdamRepository idamRepository;

    @Autowired
    public RefundsJwtGrantedAuthoritiesConverter(IdamRepository idamRepository) {
        this.idamRepository = idamRepository;
    }



    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        if (jwt.hasClaim(TOKEN_NAME) && jwt.getClaim(TOKEN_NAME).equals(ACCESS_TOKEN)) {
            UserInfo userInfo = idamRepository.getUserInfo(jwt.getTokenValue());
            return extractAuthorityFromClaims(userInfo.getRoles());
        }
        return List.of();
    }

    private List<GrantedAuthority> extractAuthorityFromClaims(List<String> roles) {
        if (Optional.ofNullable(roles).isEmpty()) {
            throw new InsufficientAuthenticationException("No roles can be extracted from user "
                                                              + "most probably due to insufficient scopes provided");
        }
        return roles.stream()
            .map(SimpleGrantedAuthority::new)
            .collect(Collectors.toList());
    }

}
