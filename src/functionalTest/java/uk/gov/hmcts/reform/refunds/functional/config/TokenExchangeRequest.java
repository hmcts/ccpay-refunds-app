package uk.gov.hmcts.reform.refunds.functional.config;

import feign.Param;
import lombok.Builder;
import lombok.Data;

@Data
@Builder(builderMethodName = "tokenExchangeRequest")
public class TokenExchangeRequest {
    @Param("username") String username;
    @Param("password") String password;
    @Param("scope") String scope;
    @Param("grant_type") String grantType;
    @Param("client_id") String clientId;
    @Param("client_secret") String clientSecret;
    @Param("redirect_uri") String redirectUri;
}
