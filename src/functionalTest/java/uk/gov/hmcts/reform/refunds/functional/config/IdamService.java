package uk.gov.hmcts.reform.refunds.functional.config;

import feign.Feign;
import feign.jackson.JacksonDecoder;
import feign.jackson.JacksonEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.gov.hmcts.reform.idam.client.models.TokenExchangeResponse;
import uk.gov.hmcts.reform.idam.client.models.test.CreateUserRequest;
import uk.gov.hmcts.reform.idam.client.models.test.UserRole;
import uk.gov.hmcts.reform.refunds.functional.response.idam.IdamGetUserDetailsResponse;

import java.util.Base64;
import java.util.UUID;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

@Service
public class IdamService {

    public static final String BEARER = "Bearer ";
    public static final String GRANT_TYPE = "password";
    public static final String SCOPES = "openid profile roles";
    public static final String SCOPES_SEARCH_USER = "openid profile roles search-user";
    private static final Logger LOG = LoggerFactory.getLogger(IdamService.class);
    private static IdamApi idamApi;
    private final TestConfigProperties testConfig;

    @Autowired
    public IdamService(TestConfigProperties testConfig) {
        this.testConfig = testConfig;
        idamApi = Feign.builder()
            .encoder(new JacksonEncoder())
            .decoder(new JacksonDecoder())
            .target(IdamApi.class, testConfig.getIdamApiUrl());
    }


    public ValidUser createUserWith(String... roles) {
        String email = nextUserEmail();
        CreateUserRequest userRequest = userRequest(email, roles);
        LOG.info("idamApi : " + idamApi.toString());
        LOG.info("userRequest : " + userRequest);
        try {
            idamApi.createUser(userRequest);
        } catch (Exception ex) {
            LOG.info(ex.getMessage());
        }

        String accessToken = authenticateUser(email, testConfig.getTestUserPassword());

        return new ValidUser(email, accessToken);
    }

    public ValidUser createUserWithSearchScope(String... roles) {
        String email = nextUserEmail();
        CreateUserRequest userRequest = userRequest(email, roles);
        LOG.info("idamApi : " + idamApi.toString());
        LOG.info("userRequest : " + userRequest.toString());
        try {
            idamApi.createUser(userRequest);
        } catch (Exception ex) {
            LOG.info(ex.getMessage());
        }

        String accessToken = authenticateUserWithSearchScope(email, testConfig.getTestUserPassword());

        return new ValidUser(email, accessToken);
    }

    public String authenticateUser(String username, String password) {
        String authorisation = username + ":" + password;
        String base64Authorisation = Base64.getEncoder().encodeToString(authorisation.getBytes());

        LOG.info("testConfig.getOauth2().getClientId() : " + testConfig.getOauth2().getClientId());
        LOG.info("testConfig.getOauth2().getRedirectUrl() : " + testConfig.getOauth2().getRedirectUrl());

        try {
            TokenExchangeResponse tokenExchangeResponse = idamApi.exchangeCode(username,
                                                                               password,
                                                                               SCOPES,
                                                                               GRANT_TYPE,
                                                                               testConfig.getOauth2().getClientId(),
                                                                               testConfig.getOauth2().getClientSecret(),
                                                                               testConfig.getOauth2().getRedirectUrl());

            return BEARER + tokenExchangeResponse.getAccessToken();
        } catch (Exception ex) {
            LOG.info(ex.getMessage());
        }
        return null;
    }

    public String authenticateUserWithSearchScope(String username, String password) {
        String authorisation = username + ":" + password;
        String base64Authorisation = Base64.getEncoder().encodeToString(authorisation.getBytes());

        LOG.info("testConfig.getIdamPayBubbleClientId() : " + testConfig.getIdamPayBubbleClientId());
        LOG.info("testConfig.getIdamPayBubbleClientRedirectUri() : " + testConfig.getIdamPayBubbleClientRedirectUri());

        try {
            TokenExchangeResponse tokenExchangeResponse = idamApi.exchangeCode(username,
                                                                               password,
                                                                               SCOPES_SEARCH_USER,
                                                                               GRANT_TYPE,
                                                                               testConfig.getIdamPayBubbleClientId(),
                                                                               testConfig.getIdamPayBubbleClientSecret(),
                                                                               testConfig.getIdamPayBubbleClientRedirectUri());

            return BEARER + tokenExchangeResponse.getAccessToken();
        } catch (Exception ex) {
            LOG.info(ex.getMessage());
        }
        return null;
    }

    public String getUserDetails(String accessToken) {
        try {
            IdamGetUserDetailsResponse idamGetUserDetailsResponse = idamApi.getDetails(accessToken);
            return idamGetUserDetailsResponse.getId();
        } catch (Exception ex) {
            LOG.info(ex.getMessage());
        }
        return null;
    }

    private CreateUserRequest userRequest(String email, String... roles) {
        return CreateUserRequest.builder()
            .email(email)
            .password(testConfig.getTestUserPassword())
            .roles(Stream.of(roles)
                       .map(UserRole::new)
                       .collect(toList()))
            .build();
    }

    private String nextUserEmail() {
        return String.format(testConfig.getGeneratedUserEmailPattern(), UUID.randomUUID());
    }

    public static void deleteUser(String emailAddress) {
        idamApi.deleteUser(emailAddress);
    }
}
